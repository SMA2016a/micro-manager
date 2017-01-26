/*
 * Copyright (c) 2015-2017, Regents the University of California
 * Author: Nico Stuurman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package edu.valelab.gaussianfit.fitting;

import edu.valelab.gaussianfit.utils.Besseli;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.function.Exp;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionMappingAdapter;

/**
 * Implements fitting of pairwise distribution function as described in
 *         http://dx.doi.org/10.1529/biophysj.105.065599
 * 
 * @author nico
 */
class P2DFunc implements MultivariateFunction {
   private final double[] points_;
   private final double sigma_;
   private final boolean fitSigma_;
   
   /**
    * 
    * @param points array with measurements
    * @param fitSigma whether sigma value should be fitted
    * @param sigma  Fixed sigma, only needed when fitSigma is true
    */
   public P2DFunc(double[] points, final boolean fitSigma, final double sigma) {
      points_ = points;
      fitSigma_ = fitSigma;
      sigma_ = sigma;
   }
   
   /**
    * Calculate the sum of the likelihood function
    * @param doubles array with parameters, here doubles[0] == mu, 
    * doubles [1] == sigma
    * @return -sum(logP2D(points, mu, sigma));
    */
   @Override
   public double value(double[] doubles) {
      double sum = 0;
      double sigma = sigma_;
      if (fitSigma_) {
         sigma = doubles[1];
      }
      for (double point : points_) {
         double predictedValue = P2DFitter.p2d(point, doubles[0], sigma);
         sum += Math.log(predictedValue);
      }
      return -sum;
   }
     
}


class P2D50 implements MultivariateFunction {
   private final double target_;
   private final double mu_;
   private final double sigma_;
   
   /**
    * 
    * @param points array with measurements
    * @param fitSigma whether sigma value should be fitted
    * @param sigma  Fixed sigma, only needed when fitSigma is true
    */
   public P2D50(double target, double mu, double sigma) {
      target_ = target;
      mu_ = mu;
      sigma_ = sigma;
   }
   
   /**
    * Calculates the difference of the likelihood with the target
    * @param doubles array of size 1 containing r (i.e. we are looking 
    * for the r that gives a value closest to target_)
    * @return target - P2D(r, mu, sigma));
    */
   @Override
   public double value(double[] doubles) {
      double result = target_ - P2DFitter.p2d(doubles[0], mu_, sigma_);
      return result * result;
   }
    

   
}

/**
 * Class that uses the apache commons math3 library to fit a maximum likelihood
 * function for the probability density function of the distribution of measured
 * distances.
 * @author nico
 */

public class P2DFitter {
   private final double[] points_;
   private double muGuess_ = 0.0;
   private double sigmaGuess_ = 10.0;
   private final double upperBound_;
   private final boolean fitSigma_;
   
   
    /**
    * Calculates the probability density function:
    * p2D(r) = (r / sigma2) exp(-(mu2 + r2)/2sigma2) I0(rmu/sigma2)
    * where I0 is the modified Bessel function of integer order zero
    * @param r
    * @param mu
    * @param sigma
    * @return 
    */
   public static double p2d (double r, double mu, double sigma) {
      double first = r / (sigma * sigma);
      Exp exp = new Exp();
      double second = exp.value(- (mu * mu + r * r)/ (2 * sigma * sigma));
      double third = Besseli.bessi(0, (r * mu) / (sigma * sigma) );
      
      return first * second * third;
   }
   
   
   
   /**
    * 
    * @param points array with data points to be fitted
    * @param fitSigma whether or not sigma should be fitted.  When false, the
    *                sigmaEstimate given in setStartParams will be used as 
    *                a fixed parameter in the P2D function
    * @param upperBound Upper bound for average and sigma
    */
   public P2DFitter(double[] points, final boolean fitSigma, 
           final double upperBound) {
      points_ = points;
      fitSigma_ = fitSigma;
      upperBound_ = upperBound;
   }
   
   /**
    * Lets caller provide start parameters for fit of mu and sigma
    * @param mu
    * @param sigma 
    */
   public void setStartParams(double mu, double sigma) {
      muGuess_ = mu;
      sigmaGuess_ = sigma;
   }
   
   /**
    * Given estimators for mu and sigma, what is the log likelihood for this
    * distribution of data?
    * @param estimators - array containing mu, and - if fitSigma is true sigma
    *       (i.e. the array returned from the solve function can be used here).
    * @return negative log likelihood for the data set given in the constructor
    */
   public double logLikelihood(double[] estimators) {
      P2DFunc myP2DFunc = new P2DFunc(points_, fitSigma_, sigmaGuess_);
      return myP2DFunc.value(estimators);
   }
      
   public double[] solve() throws FittingException {
      SimplexOptimizer optimizer = new SimplexOptimizer(1e-9, 1e-12);
      P2DFunc myP2DFunc = new P2DFunc(points_, fitSigma_, sigmaGuess_);

      if (fitSigma_) {
         double[] lowerBounds = {0.0, 0.0};
         double[] upperBounds = {upperBound_, upperBound_};
         MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
                 myP2DFunc, lowerBounds, upperBounds);

         PointValuePair solution = optimizer.optimize(
                 new ObjectiveFunction(mfma),
                 new MaxEval(500),
                 GoalType.MINIMIZE,
                 new InitialGuess(mfma.boundedToUnbounded(new double[]{muGuess_, sigmaGuess_})),
                 new NelderMeadSimplex(new double[]{0.2, 0.2})//,
         );

         return mfma.unboundedToBounded(solution.getPoint());
      } else {
         double[] lowerBounds = {0.0};
         double[] upperBounds = {upperBound_};
         MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
                 myP2DFunc, lowerBounds, upperBounds);

         try {
         PointValuePair solution = optimizer.optimize(
                 new ObjectiveFunction(mfma),
                 new MaxEval(500),
                 GoalType.MINIMIZE,
                 new InitialGuess(mfma.boundedToUnbounded(new double[]{muGuess_})),
                 new NelderMeadSimplex(new double[]{0.2})//,
         );

         return mfma.unboundedToBounded(solution.getPoint());
         } catch (TooManyEvaluationsException tmee) {
            throw new FittingException("P2D fit faled due to too many Evaluation Exceptions");
         }
      }
   }

   public double[] muIntervals(double mu, double sigma) throws FittingException {
      P2D50 p2d50 = new P2D50(0, mu, sigma);
      SimplexOptimizer optimizer = new SimplexOptimizer(1e-9, 1e-12);

      try {
         // first get the distance where the likelihood is highest
         double[] lowerBounds = {0.0};
         double[] upperBounds = {upperBound_};
         MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(
              p2d50, lowerBounds, upperBounds);
         PointValuePair msPVP = optimizer.optimize(
                 new ObjectiveFunction(mfma),
                 new MaxEval(500),
                 GoalType.MAXIMIZE,
                 new InitialGuess(mfma.boundedToUnbounded(new double[]{mu})),
                 new NelderMeadSimplex(new double[]{0.2})//,
         );
         double[] maxR = mfma.unboundedToBounded(msPVP.getPoint());
         
         // given the distance where the likelihood is highest, calculate the likelihood
         double maxL = p2d(maxR[0], mu, sigma);
         
         // Now find the distances where the likelihood is 0.5 * max likelihood
         
         P2D50 p2d50b = new P2D50(0.5 * maxL, mu, sigma);
         mfma = new MultivariateFunctionMappingAdapter(p2d50b, lowerBounds, 
                 upperBounds);
         
         PointValuePair lsPVP = optimizer.optimize(
                 new ObjectiveFunction(mfma),
                 new MaxEval(500),
                 GoalType.MINIMIZE,
                 new InitialGuess(mfma.boundedToUnbounded(new double[]{0.5 * maxR[0]})),
                 new NelderMeadSimplex(new double[]{0.2})//,
         );
         double[] lowerSolution = mfma.unboundedToBounded(lsPVP.getPoint());
         
                 PointValuePair usPVP = optimizer.optimize(
                 new ObjectiveFunction(mfma),
                 new MaxEval(500),
                 GoalType.MINIMIZE,
                 new InitialGuess(mfma.boundedToUnbounded(new double[]{4.0 * maxR[0]})),
                 new NelderMeadSimplex(new double[]{0.2})//,
         );
         double[] upperSolution = mfma.unboundedToBounded(usPVP.getPoint());
         
         return new double[] {lowerSolution[0], upperSolution[0]};      
         
      } catch (TooManyEvaluationsException tmee) {
         throw new FittingException("P2D fit faled due to too many Evaluation Exceptions");
      }
   }
            
}
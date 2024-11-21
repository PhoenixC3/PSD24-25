package com.peerapp;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Base64;

public class SecretSharing {
    private static final BigInteger field = new BigInteger("8CF83642A709A097B447997640129DA299B1A47D1EB3750BA308B0FE64F5FBD3", 16);
    private static final SecureRandom rndGenerator = new SecureRandom();

    /**
     * This method shares a secret using Shamir's scheme.
     * @param polyDegree Degree of the polynomial.
     * @param nShareholders Number of shareholders.
     * @param secret Secret to share.
     * @return Shares of the secret.
     */
    public static Share[] share(int polyDegree, int nShareholders, BigInteger secret) {
        //creating polynomial: P(x) = a_d * x^d + ... + a_1 * x^1 + secret
        BigInteger[] polynomial = new BigInteger[polyDegree + 1];

        polynomial[0] = secret;

        for (int i = 1; i <= polyDegree; i++) {
            polynomial[i] = new BigInteger(field.bitLength() - 1, rndGenerator);
        }

        //calculating shares
        Share[] shares = new Share[nShareholders];
        for (int i = 0; i < nShareholders; i++) {
            BigInteger shareholder = BigInteger.valueOf(i + 1); //shareholder id can be any positive number, except 0
            BigInteger share = calculatePoint(shareholder, polynomial);
            shares[i] = new Share(shareholder, share);
        }

        return shares;
    }

    /**
     * This method combines shares, using Lagrange polynomials, to recover the secret.
     * 	Lagrange polynomials: https://en.wikipedia.org/wiki/Lagrange_polynomial.
     * @param shares Shares of the secret.
     * @return Recovered secret.
     */
    public static BigInteger combine(Share[] shares) {

        BigInteger accum = BigInteger.ZERO;

        for(int i = 0; i < shares.length; i++)
        {
            BigInteger numerator = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;

            for(int j = 0; j < shares.length; j++)
            {
                if(i == j)
                    continue;

                BigInteger startposition = shares[i].getShareholder();
                BigInteger nextposition = shares[j].getShareholder();

                numerator = numerator.multiply(nextposition.negate()).mod(field); //(numerator * -nextposition) % prime;
                denominator = denominator.multiply(startposition.subtract(nextposition).mod(field)); //(denominator * (startposition - nextposition)) % prime;
            }

            BigInteger value = shares[i].getShare();
            BigInteger tmp = value.multiply(numerator).multiply(denominator.mod(field));

            accum = field.add(accum).add(tmp).mod(field); //(prime + accum + (value * numerator * modInverse(denominator))) % prime;
        }

        return accum;

    }

    /**
     * This method calculates a point on a polynomial using the Horner's method:
     * 	https://en.wikipedia.org/wiki/Horner%27s_method.
     * @param x X value.
     * @param polynomial Polynomial P(x).
     * @return Y value.
     */
    private static BigInteger calculatePoint(BigInteger x, BigInteger[] polynomial) {
        BigInteger y = new BigInteger(field.bitLength() - 1, rndGenerator);
        
        // Evaluate value of polynomial using Horner's method
        for (int i = polynomial.length - 1; i >= 0; i--)
            y = polynomial[i].add((x.multiply(y).mod(field))).mod(field);
        
        return y;
    }

    public static class Share implements Serializable {
		private static final long serialVersionUID = 1L;
		private final String shareholder;
		private final String share;
	
		public Share(BigInteger shareholder, BigInteger share) {
			this.shareholder = Base64.getEncoder().encodeToString(shareholder.toByteArray());
			this.share = Base64.getEncoder().encodeToString(share.toByteArray());
		}
	
		public BigInteger getShareholder() {
			return new BigInteger(Base64.getDecoder().decode(shareholder));
		}
	
		public BigInteger getShare() {
			return new BigInteger(Base64.getDecoder().decode(share));
		}
	
		@Override
		public String toString() {
			return "Shareholder: " + shareholder + ", Share: " + share;
		}
	}
}
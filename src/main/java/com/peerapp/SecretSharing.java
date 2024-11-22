package com.peerapp;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.SecureRandom;

public class SecretSharing {
    private static final SecureRandom rndGenerator = new SecureRandom();
    private static final BigInteger prime = new BigInteger("2519590847565789349402718324004839857142928212620403202777713783604366202070759555626401852588078440691829064124951508218929855914917618450280848912007284499268739280728777673597141834727026189637501497182463702599997597560100155833172321706224017071668293956073953150173793464361151274682401831328486076953941570176145648602578799902990879079158426146818865235321237320339514186339124047927475042355357967595348502041522235616972291891624075761199380523512031047744387085125553289688181630418662850513792321978457720170006081200191732483938561616061934030440486478513867381613608443344385692901934375954268650187427321673829664502685162791162153902939775867443143483181257338458269044353943026654186605546007373665753651065883352359227946601909311295531042410084100995904980533049245546990647019987443622358942287959512048453105222631929445993004256385338745209656574316803476328859508172542146325643863032558654909316310769909149943056458481668054704824858460595937838641077582709538349816035966452415363887019284828397464114744094575601291120834219441990136459148903003678319807232260473329626325369702769285034895132069464807346274166940200320107904436471630662632741931228419216811395797564770617827978231240544346212073357057820413516608972833842743935863813044904362290784029661087914894217306648828362280181229778550341289581644749693757");


    public static Share[] share(int polyDegree, int nShareholders, BigInteger secret) {
        BigInteger[] polynomial = new BigInteger[polyDegree + 1];
        polynomial[0] = secret;

        for (int i = 1; i <= polyDegree; i++) {
            polynomial[i] = new BigInteger(prime.bitLength() - 1, rndGenerator).mod(prime);
        }

        // Calculate shares
        Share[] shares = new Share[nShareholders];
        for (int i = 0; i < nShareholders; i++) {
            BigInteger x = BigInteger.valueOf(i + 1);
            BigInteger y = calculatePoint(x, polynomial);
            shares[i] = new Share(x, y);
        }

        return shares;
    }

    private static BigInteger calculatePoint(BigInteger x, BigInteger[] polynomial) {
        BigInteger y = BigInteger.ZERO;

        for (int i = polynomial.length - 1; i >= 0; i--) {
            y = y.multiply(x).add(polynomial[i]).mod(prime);
        }

        return y;
    }

    public static BigInteger combine(Share[] shares) {
        BigInteger accum = BigInteger.ZERO;

        for (int i = 0; i < shares.length; i++) {
            BigInteger numerator = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;

            for (int j = 0; j < shares.length; j++) {
                if (i == j) continue;

                BigInteger xi = shares[i].getX();
                BigInteger xj = shares[j].getX();

                numerator = numerator.multiply(xj.negate()).mod(prime);
                denominator = denominator.multiply(xi.subtract(xj)).mod(prime);
            }

            BigInteger lagrangeCoefficient = numerator.multiply(denominator.modInverse(prime)).mod(prime);
            accum = accum.add(shares[i].getY().multiply(lagrangeCoefficient)).mod(prime);
        }

        return accum;
    }

    public static class Share implements Serializable {
        private final BigInteger x;
        private final BigInteger y;

        public Share(BigInteger x, BigInteger y) {
            this.x = x;
            this.y = y;
        }

        public BigInteger getX() {
            return x;
        }

        public BigInteger getY() {
            return y;
        }

        @Override
        public String toString() {
            return "Share{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }
    }
}
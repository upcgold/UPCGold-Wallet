package com.alphawallet.app.di;

import android.arch.lifecycle.MutableLiveData;

import com.alphawallet.app.contracts.Permissions;
import com.alphawallet.app.entity.Wallet;

import java.math.BigInteger;

import java8.util.concurrent.CompletableFuture;

public class UPCSingleton {


    private static UPCSingleton singleton = new UPCSingleton( );

    /* A private Constructor prevents any other
     * class from instantiating.
     */
    private UPCSingleton() { }

    public MutableLiveData<Wallet> defaultWallet;
    public String bankAddress = "0xF5e14DD3C82C9a38C8159F79AcBe767e34a240F3";
    public String myAddress = "";
    public boolean isCoinboxClient = false;
    //public String scanButtonPressed = "crypto_scan_button";
    public String scanButtonPressed = "";

    /* Static 'instance' method */
    public static UPCSingleton getInstance( ) {
        return singleton;
    }

    /* Other methods protected by singleton-ness */
    protected static void demoMethod( ) {
        System.out.println("demoMethod for singleton");
    }


    public String clientRegister(Permissions permissions) {

        try {
            //CompletableFuture<BigInteger> balance = bank.getBalance().sendAsync();
            //totalBalance = balance.get();

            CompletableFuture<Permissions.ClientMode> clientInfo = permissions.findClient(myAddress).sendAsync();
            Permissions.ClientMode clientResult = clientInfo.get();
            String client = clientResult.client;
            BigInteger timestamp = clientResult.timestamp;

            //if timestamp is greater than zero, then this user has scanned a client qr
            if(timestamp.compareTo(BigInteger.valueOf(0)) > 0) {
                isCoinboxClient = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public boolean coinboxCheck(Permissions permissions) {

        boolean isClient = false;
        try {
            //CompletableFuture<BigInteger> balance = bank.getBalance().sendAsync();
            //totalBalance = balance.get();

            CompletableFuture<Permissions.ClientMode> clientInfo = permissions.findClient(myAddress).sendAsync();
            Permissions.ClientMode clientResult = clientInfo.get();
            String client = clientResult.client;
            BigInteger timestamp = clientResult.timestamp;

            //if timestamp is greater than zero, then this user has scanned a client qr
            if(timestamp.compareTo(BigInteger.valueOf(0)) > 0) {
                isClient = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return isClient;
    }

    public String buildPayloadForString(String word) {
        String finalPayload = "";
        String wordLength = Integer.toHexString(word.length());
        String lengthZeros = "";
        String lengthZerosGameId = "";

        int numZeros = 65 - wordLength.length();

        for(int i = 0; i < numZeros; i++) {
            lengthZeros += "0";
        }

        String formattedWordLength = lengthZeros + wordLength;
        finalPayload += formattedWordLength;



        char[] wordToChar = word.toCharArray();
        StringBuilder builder = new StringBuilder();
        for (char c : wordToChar) {
            // Step-2 Use %H to format character to Hex
            String hexCode=String.format("%H", c);
            builder.append(hexCode);
        }

        int bLength = 64 - builder.length();
        while(bLength > 0) {
            builder.append('0');
            bLength--;
        }

        finalPayload += builder;


        return finalPayload;

    }


    public String buildPayload(String word, String gameId) {
        //String finalPayload = "0x2b8f7a49000000000000000000000000000000000000000000000000000000000000002";
        String finalPayload = "0xb389d92a0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008";


        String wordPayload= buildPayloadForString(word);
        finalPayload += wordPayload;

        String shouldLoadd = "0xb389d92a00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000000c3838353931313434353336380000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f536176696e6773204163636f756e740000000000000000000000000000000000";
        String gamePayload= buildPayloadForString(gameId);

        //todo figure out why substring is needed.  the game param was returning with an exxtra '0' prepended to the payloadString
        finalPayload += gamePayload.substring(1);
        return finalPayload;
   }
}

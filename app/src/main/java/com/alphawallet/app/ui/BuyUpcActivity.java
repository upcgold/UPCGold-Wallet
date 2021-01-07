package com.alphawallet.app.ui;

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.TransactionTooLargeException;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import com.alphawallet.app.di.UPCSingleton;
import com.alphawallet.app.C;
import com.alphawallet.app.R;
import com.alphawallet.app.contracts.UPCGoldBank;
import com.alphawallet.app.entity.ConfirmationType;
import com.alphawallet.app.entity.NetworkInfo;
import com.alphawallet.app.entity.QRResult;
import com.alphawallet.app.entity.Wallet;
import com.alphawallet.app.entity.tokens.Token;
import com.alphawallet.app.entity.tokens.TokenInfo;
import com.alphawallet.app.repository.TokenRepository;
import com.alphawallet.app.repository.TokenRepositoryType;
import com.alphawallet.app.router.ConfirmationRouter;
import com.alphawallet.app.ui.widget.OnQRCodeScannedListener;
import com.alphawallet.app.ui.widget.entity.AmountEntryItem;
import com.alphawallet.app.ui.widget.entity.ENSHandler;
import com.alphawallet.app.ui.zxing.FullScannerFragment;
import com.alphawallet.app.util.BalanceUtils;
import com.alphawallet.app.util.Utils;
import com.alphawallet.app.viewmodel.DappBrowserViewModel;
import com.alphawallet.app.viewmodel.SendViewModel;
import com.alphawallet.app.web3.OnSignTransactionListener;
import com.alphawallet.app.web3.entity.Address;
import com.alphawallet.app.web3.entity.Web3Transaction;
import com.alphawallet.app.widget.AWalletAlertDialog;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Uint;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.utils.Convert;

import java.lang.ref.SoftReference;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;

import javax.inject.Inject;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.alphawallet.app.C.Key.WALLET;
import static com.alphawallet.app.repository.EthereumNetworkBase.XDAI_ID;
import static com.alphawallet.token.tools.Convert.getEthString;

public class BuyUpcActivity extends BaseActivity implements OnQRCodeScannedListener, OnSignTransactionListener {
    private static final int BARCODE_READER_REQUEST_CODE = 1;
    @Inject
    SendViewModel viewModel;
    private DappBrowserViewModel dappViewModel;


    private final MutableLiveData<Wallet> defaultWallet = new MutableLiveData<>();
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    public static final int RC_HANDLE_IMAGE_PICKUP = 3;

    public static final int DENY_PERMISSION = 1;
    protected TokenRepositoryType tokenRepository;

    private FullScannerFragment fullScannerFragment;

    private TextView flashButton;
    private TextView myAddressButton;
    private TextView browseButton;
    private Disposable disposable;
    private AWalletAlertDialog dialog;
    private ImageButton scanQrImageView;
    private TextView tokenBalanceText;
    private TextView tokenSymbolText;

    private TextView upcRaw;
    private Spinner gameSpinner;
    private int gameId;

    private TextView totalBalance;
    private TextView currentStaker;
    private TextView amountStaked;

    private TextView pasteText;
    private Button nextBtn;
    private String currentAmount;
    private QRResult currentResult;

    private String myAddress;
    private int decimals;
    private String symbol;
    private Wallet wallet;
    private Token token;
    private String contractAddress;
    private ENSHandler ensHandler;
    private Handler handler;
    private TextView chainName;
    private EditText toStake;



    private int currentChain;
    private AmountEntryItem amountInput;
    private volatile boolean canSign = true;
    private NetworkInfo networkInfo;
    private ConfirmationType confirmationType;
    private  ConfirmationRouter confirmationRouter;

    @Override
    public void onCreate(Bundle state)
    {
        super.onCreate(state);
        setContentView(R.layout.activity_buy_upc);
        initView();
        confirmationRouter = new ConfirmationRouter();
    }

    private void initView() {
        String upcString;
        String totalBalanceString;
        String currentStakerString;
        String amountStakedString;

        upcRaw = findViewById(R.id.upc_raw);
        gameSpinner  = findViewById(R.id.game_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.game_id, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gameSpinner.setAdapter(adapter);



        currentStaker = findViewById(R.id.current_staker);
        amountStaked = findViewById(R.id.amount_staked);
        toStake = findViewById(R.id.to_stake);


        if (getIntent() != null) {
            upcString = getIntent().getStringExtra("upc_raw");
            //totalBalanceString = getIntent().getStringExtra("total_balance");
            currentStakerString = getIntent().getStringExtra("current_staker");
            amountStakedString  = getIntent().getStringExtra("amount_staked");

            upcRaw.setText(upcString);
            //totalBalance.setText(totalBalanceString);
            String addyBegin = currentStakerString.substring(0,10);
            String addyEnd =   currentStakerString.substring(currentStakerString.length()-5);
            String addyFinal = addyBegin + "..." + addyEnd;
            String amountString = Convert.fromWei(amountStakedString,Convert.Unit.ETHER).toString() + "(xDAI)";

            if(addyFinal.equals("0x00000000...00000") ){
                addyFinal = "Vacant";
            }

            currentStaker.setText(addyFinal);
            amountStaked.setText(amountString);

        }

        nextBtn = findViewById(R.id.button_next);
        nextBtn.setOnClickListener(v -> {
            onNext();
        });
    }


    @Override
    public void onSignTransaction(Web3Transaction transaction, String url)
    {
        try
        {
            dappViewModel.updateGasPrice(networkInfo.chainId); //start updating gas price right before we open
            //minimum for transaction to be valid: recipient and value or payload
            if ((transaction.recipient.equals(Address.EMPTY) && transaction.payload != null) // Constructor
                    || (!transaction.recipient.equals(Address.EMPTY) && (transaction.payload != null || transaction.value != null))) // Raw or Function TX
            {
                if (canSign)
                {
                    dappViewModel.openConfirmation(this, transaction, url, networkInfo);
                    canSign = false;
                    handler.postDelayed(() -> canSign = true, 3000); //debounce 3 seconds to avoid multiple signing issues
                }
            }
            else
            {
                //display transaction error
                //onInvalidTransaction(transaction);
                //web3.onSignCancel(transaction);
            }
        }
        catch (android.os.TransactionTooLargeException e)
        {
            //transactionTooLarge();
            //web3.onSignCancel(transaction);
        }
        catch (Exception e)
        {
            //onInvalidTransaction(transaction);
            //web3.onSignCancel(transaction);
        }
    }



    private void onNext() {

        UPCSingleton singleton = UPCSingleton.getInstance();
        //String contractAddress = "0xbE0e4C218a78a80b50aeE895a1D99C1D7a842580";
        String contractAddressStr = singleton.bankAddress;
        //String contractAddressStr = "0xbE0e4C218a78a80b50aeE895a1D99C1D7a842580";


      //String payload = "0x2b8f7a49000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000117468697369736174657374737472696e67000000000000000000000000000000";
        //String payload = "0x2b8f7a490000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000a626f6f6e65736661726d00000000000000000000000000000000000000000000";

        String selectedGame = gameSpinner.getSelectedItem().toString();
        String payload = singleton.buildPayload(upcRaw.getText().toString(),selectedGame );


        String gasString = "1";
        Address contratAddress = new Address(contractAddressStr);
        BigInteger gasPrice = Convert.toWei(gasString,Convert.Unit.GWEI).toBigInteger();
        BigInteger gasLimit = BigInteger.valueOf(12487794);
        String amountStakedString = toStake.getText().toString();
        BigDecimal convertecd = Convert.toWei(amountStakedString,Convert.Unit.ETHER);

        Long nonce = Long.valueOf(-1);

        Web3Transaction trans = new Web3Transaction(
                new Address(contractAddressStr),
                contratAddress,
                convertecd.toBigInteger(),
                gasPrice,
                gasLimit,
                nonce,
                payload
        );

        String url = "https://bank.upcgold.io/";
        try {
            confirmationRouter.open(this, trans, "xDai", url, 100);
        }
        catch (TransactionTooLargeException e) {

        }

    }

    private void pickImage()
    {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), RC_HANDLE_IMAGE_PICKUP);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean handled = false;

        if (requestCode == RC_HANDLE_CAMERA_PERM)
        {
            for (int i = 0; i < permissions.length; i++)
            {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                if (permission.equals(Manifest.permission.CAMERA))
                {
                    if (grantResult == PackageManager.PERMISSION_GRANTED)
                    {
                        setContentView(R.layout.activity_full_screen_scanner_fragment);
                        initView();
                        handled = true;
                    }
                }
            }
        }
        else if (requestCode == RC_HANDLE_IMAGE_PICKUP)
        {
            pickImage();
            handled = true;
        }

        // Handle deny permission
        if (!handled)
        {
            Intent intent = new Intent();
            setResult(DENY_PERMISSION, intent);
            finish();
        }
    }

    @Override
    public void onReceive(String result)
    {
        handleQRCode(result);
    }

    public void handleQRCode(String qrCode)
    {
        if (qrCode.startsWith("wc:")) {
            startWalletConnect(qrCode);
        } else {

        }
    }

    private void startWalletConnect(String qrCode) {
        Intent intent = new Intent(this, WalletConnectActivity.class);
        intent.putExtra("qrCode", qrCode);
        startActivity(intent);
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onBackPressed()
    {
        Intent intent = new Intent();
        setResult(Activity.RESULT_CANCELED, intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_HANDLE_IMAGE_PICKUP && resultCode == Activity.RESULT_OK)
        {
            if (data != null) {
                Uri selectedImage = data.getData();

                disposable = concertAndHandle(selectedImage)
                        .observeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::onSuccess, this::onError);
            }
        }
    }

    private void onError(Throwable throwable)
    {
        displayErrorDialog(getString(R.string.title_dialog_error), getString(R.string.error_browse_selection));
    }

    private void onSuccess(Result result)
    {
        if (result == null)
        {
            displayErrorDialog(getString(R.string.title_dialog_error), getString(R.string.error_browse_selection));
        }
        else
        {
            handleQRCode(result.getText());
        }
    }

    private Single<Result> concertAndHandle(Uri selectedImage)
    {
        return Single.fromCallable(() -> {

            SoftReference<Bitmap> softReferenceBitmap;
            softReferenceBitmap = new SoftReference<>(MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage));

            if (softReferenceBitmap.get() != null)
            {
                int width = softReferenceBitmap.get().getWidth(), height = softReferenceBitmap.get().getHeight();
                int[] pixels = new int[width * height];
                softReferenceBitmap.get().getPixels(pixels, 0, width, 0, 0, width, height);
                softReferenceBitmap.get().recycle();
                softReferenceBitmap.clear();
                RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
                BinaryBitmap bBitmap = new BinaryBitmap(new HybridBinarizer(source));
                MultiFormatReader reader = new MultiFormatReader();
                return reader.decodeWithState(bBitmap);
            }

            return null;
        });
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void stop()
    {
        if (disposable != null && !disposable.isDisposed())
        {
            disposable.dispose();
        }
    }

    private void displayErrorDialog(String title, String errorMessage)
    {
        AWalletAlertDialog aDialog = new AWalletAlertDialog(this);
        aDialog.setTitle(title);
        aDialog.setMessage(errorMessage);
        aDialog.setIcon(AWalletAlertDialog.ERROR);
        aDialog.setButtonText(R.string.button_ok);
        aDialog.setButtonListener(v -> {
            aDialog.dismiss();
        });
        dialog = aDialog;
        dialog.show();
    }
}
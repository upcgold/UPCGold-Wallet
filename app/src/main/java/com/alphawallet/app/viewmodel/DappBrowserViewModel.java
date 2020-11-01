package com.alphawallet.app.viewmodel;

import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.os.TransactionTooLargeException;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.webkit.WebView;
import android.widget.Toast;

import com.alphawallet.app.R;
import com.alphawallet.app.entity.Operation;
import com.alphawallet.app.entity.QRResult;
import com.alphawallet.app.repository.TokenRepository;
import com.alphawallet.app.service.TokensService;
import com.alphawallet.app.ui.MyAddressActivity;
import com.alphawallet.app.ui.BuyUpcActivity;
import com.alphawallet.app.ui.SendActivity;
import com.alphawallet.app.ui.WalletConnectActivity;
import com.alphawallet.token.entity.Signable;
import com.alphawallet.app.C;

import com.alphawallet.app.contracts.UPCGoldBank;
import com.alphawallet.app.entity.DApp;
import com.alphawallet.app.entity.DAppFunction;
import com.alphawallet.app.entity.NetworkInfo;
import com.alphawallet.app.entity.SignAuthenticationCallback;
import com.alphawallet.app.entity.Wallet;
import com.alphawallet.app.entity.tokens.Token;
import com.alphawallet.app.interact.CreateTransactionInteract;
import com.alphawallet.app.interact.FindDefaultNetworkInteract;
import com.alphawallet.app.interact.GenericWalletInteract;
import com.alphawallet.app.repository.EthereumNetworkRepositoryType;
import com.alphawallet.app.router.ConfirmationRouter;
import com.alphawallet.app.service.AssetDefinitionService;
import com.alphawallet.app.service.GasService;
import com.alphawallet.app.service.KeyService;
import com.alphawallet.app.ui.AddEditDappActivity;
import com.alphawallet.app.ui.HomeActivity;
import com.alphawallet.app.ui.ScanUpcActivity;
import com.alphawallet.app.ui.ImportTokenActivity;
import com.alphawallet.app.ui.zxing.QRScanningActivity;
import com.alphawallet.app.util.DappBrowserUtils;
import com.alphawallet.app.web3.entity.Web3Transaction;

import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tuples.generated.Tuple4;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;
import java8.util.concurrent.CompletableFuture;

import static com.alphawallet.app.C.Key.WALLET;
import static com.alphawallet.app.repository.EthereumNetworkBase.XDAI_ID;

public class DappBrowserViewModel extends BaseViewModel  {
    private final MutableLiveData<NetworkInfo> defaultNetwork = new MutableLiveData<>();
    private final MutableLiveData<Wallet> defaultWallet = new MutableLiveData<>();

    private static final int BALANCE_CHECK_INTERVAL_SECONDS = 20;

    private final FindDefaultNetworkInteract findDefaultNetworkInteract;
    private final GenericWalletInteract genericWalletInteract;
    private final AssetDefinitionService assetDefinitionService;
    private final CreateTransactionInteract createTransactionInteract;
    private final TokensService tokensService;
    private final ConfirmationRouter confirmationRouter;
    private final EthereumNetworkRepositoryType ethereumNetworkRepository;
    private final KeyService keyService;
    private final GasService gasService;

    @Nullable
    private Disposable balanceTimerDisposable;

    public DappBrowserViewModel(
            FindDefaultNetworkInteract findDefaultNetworkInteract,
            GenericWalletInteract genericWalletInteract,
            AssetDefinitionService assetDefinitionService,
            CreateTransactionInteract createTransactionInteract,
            TokensService tokensService,
            ConfirmationRouter confirmationRouter,
            EthereumNetworkRepositoryType ethereumNetworkRepository,
            KeyService keyService,
            GasService gasService) {
        this.findDefaultNetworkInteract = findDefaultNetworkInteract;
        this.genericWalletInteract = genericWalletInteract;
        this.assetDefinitionService = assetDefinitionService;
        this.createTransactionInteract = createTransactionInteract;
        this.tokensService = tokensService;
        this.confirmationRouter = confirmationRouter;
        this.ethereumNetworkRepository = ethereumNetworkRepository;
        this.keyService = keyService;
        this.gasService = gasService;
    }

    public AssetDefinitionService getAssetDefinitionService() {
        return assetDefinitionService;
    }

    public LiveData<NetworkInfo> defaultNetwork() {
        return defaultNetwork;
    }

    public LiveData<Wallet> defaultWallet() {
        return defaultWallet;
    }

    public String getNetworkName()
    {
        if (defaultNetwork.getValue().chainId == 1)
        {
            return "";
        }
        else
        {
            return defaultNetwork.getValue().name;
        }
    }

    public void prepare(Context context) {

        disposable = findDefaultNetworkInteract
                .find()
                .subscribe(this::onDefaultNetwork, this::onError);
    }

    private void onDefaultNetwork(NetworkInfo networkInfo) {
        defaultNetwork.postValue(networkInfo);
        disposable = genericWalletInteract
                .find()
                .subscribe(this::onDefaultWallet, this::onError);
    }

    private void onDefaultWallet(final Wallet wallet) {
        defaultWallet.setValue(wallet);
        //get the chain balance
        startBalanceUpdate();
    }

    private void checkBalance(final Wallet wallet)
    {
        if (defaultNetwork.getValue() != null && wallet != null)
        {
            disposable = tokensService.getChainBalance(wallet.address.toLowerCase(), defaultNetwork.getValue().chainId)
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .subscribe(w -> { }, e -> { });
        }
    }

    public Observable<Wallet> getWallet() {
        if (defaultWallet().getValue() != null) {
            return Observable.fromCallable(() -> defaultWallet().getValue());
        } else
            return findDefaultNetworkInteract.find()
                    .flatMap(networkInfo -> genericWalletInteract
                            .find()).toObservable();
    }

    public void signMessage(Signable message, DAppFunction dAppFunction) {
        disposable = createTransactionInteract.sign(defaultWallet.getValue(), message,
                defaultNetwork.getValue().chainId)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(sig -> dAppFunction.DAppReturn(sig.signature, message),
                        error -> dAppFunction.DAppError(error, message));
    }

    public void openConfirmation(Activity context, Web3Transaction transaction, String requesterURL, NetworkInfo networkInfo) throws TransactionTooLargeException
    {
        confirmationRouter.open(context, transaction, networkInfo.name, requesterURL, networkInfo.chainId);
    }

    public void setLastUrl(Context context, String url) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(C.DAPP_LASTURL_KEY, url).apply();
    }

    public void addToMyDapps(Context context, String title, String url) {
        Intent intent = new Intent(context, AddEditDappActivity.class);
        DApp dapp = new DApp(title, url);
        intent.putExtra(AddEditDappActivity.KEY_DAPP, dapp);
        intent.putExtra(AddEditDappActivity.KEY_MODE, AddEditDappActivity.MODE_ADD);
        context.startActivity(intent);
    }

    public void share(Context context, String url) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, url);
            intent.setType("text/plain");
            context.startActivity(intent);
    }

    public void onClearBrowserCacheClicked(Context context) {
        WebView webView = new WebView(context);
        webView.clearCache(true);
        Toast.makeText(context, context.getString(R.string.toast_browser_cache_cleared),
                Toast.LENGTH_SHORT).show();
    }


    //jonger start here to differentiate the standard from the crypto scan
    public void startCryptoScan(Activity activity) {
        Intent intent = new Intent(activity, ScanUpcActivity.class);
        intent.putExtra("button", "crypto");
        activity.startActivityForResult(intent, HomeActivity.DAPP_BARCODE_READER_REQUEST_CODE);
    }


    public void startScan(Activity activity) {
        Intent intent = new Intent(activity, QRScanningActivity.class);
        intent.putExtra("button", "standard");
        activity.startActivityForResult(intent, HomeActivity.DAPP_BARCODE_READER_REQUEST_CODE);
    }

    public List<DApp> getDappsMasterList(Context context) {
        return DappBrowserUtils.getDappsList(context);
    }

    public int getActiveFilterCount() {
        return ethereumNetworkRepository.getFilterNetworkList().size();
    }

    public void setNetwork(int chainId)
    {
        NetworkInfo info = ethereumNetworkRepository.getNetworkByChain(chainId);
        if (info != null)
        {
            ethereumNetworkRepository.setDefaultNetworkInfo(info);
            onDefaultNetwork(info);
        }
    }

    public void showImportLink(Context ctx, String qrCode)
    {
        Intent intent = new Intent(ctx, ImportTokenActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(C.IMPORT_STRING, qrCode);
        ctx.startActivity(intent);
    }

    public void getAuthorisation(Wallet wallet, Activity activity, SignAuthenticationCallback callback)
    {
        keyService.getAuthenticationForSignature(wallet, activity, callback);
    }

    public void resetSignDialog()
    {
        keyService.resetSigningDialog();
    }

    public void completeAuthentication(Operation signData)
    {
        keyService.completeAuthentication(signData);
    }

    public void failedAuthentication(Operation signData)
    {
        keyService.failedAuthentication(signData);
    }

    public void showSend(Context ctx, QRResult result)
    {
        Intent intent = new Intent(ctx, SendActivity.class);
        boolean sendingTokens = (result.getFunction() != null && result.getFunction().length() > 0);
        String address = defaultWallet.getValue().address;
        int decimals = 18;

        intent.putExtra(C.EXTRA_SENDING_TOKENS, sendingTokens);
        intent.putExtra(C.EXTRA_CONTRACT_ADDRESS, address);
        intent.putExtra(C.EXTRA_SYMBOL, ethereumNetworkRepository.getNetworkByChain(result.chainId).symbol);
        intent.putExtra(C.EXTRA_DECIMALS, decimals);
        intent.putExtra(C.Key.WALLET, defaultWallet.getValue());
        intent.putExtra(C.EXTRA_TOKEN_ID, (Token)null);
        intent.putExtra(C.EXTRA_AMOUNT, result);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ctx.startActivity(intent);
    }

    public void buyUpc(Context ctx, QRResult result)
    {
        String address = defaultWallet.getValue().address;
        Intent intent = new Intent(ctx, BuyUpcActivity.class);

        Web3j web3j = TokenRepository.getWeb3jService(XDAI_ID);
        ClientTransactionManager ctm = new ClientTransactionManager(web3j, address);
        String contractAddress = "0xbE0e4C218a78a80b50aeE895a1D99C1D7a842580";


        //TODO: change gasPrice and gasLimit to be dynamic values
        BigInteger gasPrice = BigInteger.valueOf(12122960);
        BigInteger gasLimit = BigInteger.valueOf(12122960);
        BigInteger totalBalance = BigInteger.valueOf(777);
        StaticGasProvider gasProvider = new StaticGasProvider(gasPrice,gasLimit);
        UPCGoldBank bank = UPCGoldBank.load(contractAddress, web3j, ctm, gasProvider );
        try {
            Intent scanIntent = new Intent(ctx, BuyUpcActivity.class);
            //CompletableFuture<BigInteger> balance = bank.getBalance().sendAsync();
            //totalBalance = balance.get();
            String addy = result.getAddress();

            CompletableFuture<Tuple4<String, BigInteger, Boolean, byte[]>> evictInfo = bank.getCostToEvict(addy).sendAsync();

            Tuple4<String, BigInteger, Boolean, byte[]> evictResult = evictInfo.get();

            String currentStaker = evictResult.component1();
            String amountStaked = evictResult.component2().toString();
            String isOwned = evictResult.component3().toString();
            byte[] upcHashBytes = evictResult.component4();

            String upcHashString = new String(upcHashBytes).toString();

            scanIntent.putExtra(WALLET, defaultWallet.getValue());
            scanIntent.putExtra("upc_raw",addy);
            scanIntent.putExtra("current_staker", currentStaker);
            scanIntent.putExtra("amount_staked", amountStaked);
            scanIntent.putExtra("is_owned", isOwned);
            scanIntent.putExtra("upc_hash", upcHashString);


            ctx.startActivity(scanIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showMyAddress(Context ctx)
    {
        Intent intent = new Intent(ctx, MyAddressActivity.class);
        intent.putExtra(WALLET, defaultWallet.getValue());
        ctx.startActivity(intent);
    }

    public void onDestroy()
    {
        if (balanceTimerDisposable != null && !balanceTimerDisposable.isDisposed()) balanceTimerDisposable.dispose();
    }

    public void updateGasPrice(int chainId)
    {
        gasService.fetchGasPriceForChain(chainId);
    }

    public Realm getRealmInstance(Wallet wallet)
    {
        return tokensService.getRealmInstance(wallet);
    }

    public void startBalanceUpdate()
    {
        if (balanceTimerDisposable == null || balanceTimerDisposable.isDisposed())
        {
            balanceTimerDisposable = Observable.interval(0, BALANCE_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS)
                    .doOnNext(l -> checkBalance(defaultWallet.getValue())).subscribe();
        }
    }

    public void stopBalanceUpdate()
    {
        if (balanceTimerDisposable != null && !balanceTimerDisposable.isDisposed()) balanceTimerDisposable.dispose();
        balanceTimerDisposable = null;
    }

    public void handleWalletConnect(Context context, String url)
    {
        String importPassData = WalletConnectActivity.WC_LOCAL_PREFIX + url;
        Intent intent = new Intent(context, WalletConnectActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra("qrCode", importPassData);
        context.startActivity(intent);
    }
}

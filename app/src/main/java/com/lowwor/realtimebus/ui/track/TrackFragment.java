package com.lowwor.realtimebus.ui.track;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import com.lowwor.realtimebus.R;
import com.lowwor.realtimebus.data.api.BusApiRepository;
import com.lowwor.realtimebus.data.local.PreferencesHelper;
import com.lowwor.realtimebus.data.model.BusStation;
import com.lowwor.realtimebus.data.model.wrapper.BusLineWrapper;
import com.lowwor.realtimebus.data.model.wrapper.BusStationWrapper;
import com.lowwor.realtimebus.data.model.wrapper.BusWrapper;
import com.lowwor.realtimebus.databinding.FragmentTrackBinding;
import com.lowwor.realtimebus.ui.MainActivity;
import com.lowwor.realtimebus.ui.base.BaseFragment;
import com.lowwor.realtimebus.ui.widget.LimitArrayAdapter;
import com.lowwor.realtimebus.utils.Constants;
import com.lowwor.realtimebus.utils.NetworkUtils;
import com.lowwor.realtimebus.utils.RxUtils;
import com.lowwor.realtimebus.viewmodel.AutoCompleteViewModel;
import com.lowwor.realtimebus.viewmodel.BusStationListViewModel;
import com.lowwor.realtimebus.viewmodel.NotificationView;
import com.lowwor.realtimebus.viewmodel.TrackViewModel;
import com.orhanobut.logger.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.TimeInterval;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by lowworker on 2015/10/15.
 */
public class TrackFragment extends BaseFragment implements NotificationView {

    @Inject
    BusApiRepository mBusApiRepository;
    @Inject
    PreferencesHelper mPreferencesHelper;


    private CompositeSubscription mSubscriptions = new CompositeSubscription();
    private Subscription mAutoRefreshSubscription = null;
    private static final int NOTIFICATION_FLAG = 1;
    private BusStationListViewModel busStationListViewModel;
    private TrackViewModel trackViewModel;
    private String mNormalLineId;
    private String mReverseLineId;
    private String mLineId;
    private String mLineName;
    private String fromStation;
    private String firstStation;
    private String lastStation;
    private AutoCompleteViewModel autoCompleteViewModel;
    private ArrayAdapter mAutoCompleteAdapter;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentTrackBinding fragmentTrackBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_track, container, false);
        ButterKnife.bind(this, fragmentTrackBinding.getRoot());
        initDependencyInjector();
        busStationListViewModel = new BusStationListViewModel(this);
        trackViewModel = new TrackViewModel();
        autoCompleteViewModel = new AutoCompleteViewModel();
        fragmentTrackBinding.setStationListViewModel(busStationListViewModel);
        fragmentTrackBinding.setTrackViewModel(trackViewModel);
        fragmentTrackBinding.setAutoCompleteViewModel(autoCompleteViewModel);
        initToolbar(fragmentTrackBinding.toolbar);
        initAutoComplete(fragmentTrackBinding.autoText);
        initSwipeRefresh(fragmentTrackBinding.swipeContainer);
        fragmentTrackBinding.executePendingBindings();
        loadStationsIfNetworkConnected();
        return fragmentTrackBinding.getRoot();
    }


    @OnClick(R.id.btn_query)
    public void onQuery() {
        loadStationsIfNetworkConnected();
    }

    @OnClick(R.id.btn_try_again)
    public void onTryAgainClick() {
        loadStationsIfNetworkConnected();
    }

    @OnClick(R.id.fab_switch)
    public void onFabSwitchClick() {
        switchDirection();
        loadBusIfNetworkConnected();
    }

    private void loadStationsIfNetworkConnected() {
        if (NetworkUtils.isNetworkAvailable(getActivity())) {
            setIsOffline(false);
            searchLine();
        } else {
            setIsOffline(true);
        }
    }

    private void loadBusIfNetworkConnected() {
        if (NetworkUtils.isNetworkAvailable(getActivity())) {
            setIsOffline(false);
            getBus();
        } else {
            setIsOffline(true);
        }
    }

    private void searchLine() {
        Logger.i("searchLine: " + autoCompleteViewModel.text.get());
        mBusApiRepository.searchLine(autoCompleteViewModel.text.get()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<BusLineWrapper>() {
            @Override
            public void onCompleted() {
                getStations();
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(BusLineWrapper busLineWrapper) {
                mLineName = busLineWrapper.getData().get(0).name;
                mPreferencesHelper.saveLastQueryLine(mLineName);

                firstStation = busLineWrapper.getData().get(0).fromStation;
                lastStation = busLineWrapper.getData().get(0).toStation;
                fromStation = getStartFrom() ? firstStation : lastStation;

                mNormalLineId = busLineWrapper.getData().get(0).id;
                mReverseLineId = busLineWrapper.getData().get(1).id;
                mLineId = getStartFrom() ? mNormalLineId : mReverseLineId;
            }
        });
    }


    private void getStations() {
        mSubscriptions.add(
                mBusApiRepository.getStationByLineId(mLineId)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Subscriber<BusStationWrapper>() {
                            @Override
                            public void onCompleted() {

                            }

                            @Override
                            public void onError(Throwable e) {
                                Logger.e("There was a problem loading the top stories " + e);
                                e.printStackTrace();
                                Toast.makeText(getActivity(), "找不到线路,请重试！", Toast.LENGTH_SHORT).show();
                                setIsLoading();
                            }

                            @Override
                            public void onNext(BusStationWrapper busStationWrapper) {
                                setIsLoading();
                                setupBusStations(busStationWrapper.getData());
                                saveAutoComplete();
                                refreshAutoComplete();
                                loadBusIfNetworkConnected();
                                executeAutoRefresh();
                            }
                        }));
    }


    private void getBus() {
//        Logger.i("getBus" + fromStation);
        mSubscriptions.add(mBusApiRepository.getBusListOnRoad(mLineName, fromStation)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(getBusSubscriber()));
    }

    public void executeAutoRefresh() {

        if (mAutoRefreshSubscription != null && !mAutoRefreshSubscription.isUnsubscribed() ) {
            mAutoRefreshSubscription.unsubscribe();
            return;
        }
        if (getAutoRefresh()) {
            mAutoRefreshSubscription =
                    Observable.interval(Constants.REFRESH_INTERVAL, TimeUnit.SECONDS)
                            .timeInterval().flatMap(new Func1<TimeInterval<Long>, Observable<BusWrapper>>() {
                        @Override
                        public Observable<BusWrapper> call(TimeInterval<Long> longTimeInterval) {
                            return mBusApiRepository.getBusListOnRoad(mLineName, fromStation);
                        }
                    })
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeOn(Schedulers.io())
                            .subscribe(getBusSubscriber());
        }
    }


    private Subscriber<BusWrapper> getBusSubscriber() {
        return new Subscriber<BusWrapper>() {
            @Override
            public void onCompleted() {

//                Logger.i("onCompleted");
            }

            @Override
            public void onError(Throwable e) {

                Logger.e("There was a problem loading bus on line " + e);
                e.printStackTrace();
                setIsLoading();
            }

            @Override
            public void onNext(BusWrapper busWrapper) {
//                Logger.i("onNext");
                setIsLoading();
                busStationListViewModel.setBuses(busWrapper.getData());
            }
        };
    }

    @Override
    public void showNotification(String busStationName) {

        Intent notificationIntent = new Intent(getActivity(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getActivity(), NOTIFICATION_FLAG, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder mBuilder = new Notification.Builder(getActivity())
                .setDefaults(Notification.DEFAULT_SOUND)
                .setTicker(busStationName + "的公交到站了")
                .setContentIntent(pendingIntent)
                .setContentTitle(busStationName + "的公交到站了")
                .setContentText(busStationName + "的公交到站了")
                .setSmallIcon(R.drawable.ic_time_to_leave);

        NotificationManager mNotificationManager =
                (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_FLAG, mBuilder.build());

        ImageView iconView = new ImageView(getActivity());
        iconView.setImageResource(R.mipmap.ic_launcher);
    }


    private void switchDirection() {
        switchStartFrom();
        getStations();
    }


    private void setupBusStations(List<BusStation> busStations) {
        busStationListViewModel.setItems(busStations);
    }

    private void switchStartFrom() {
        mPreferencesHelper.saveStartFromFirst(!getStartFrom());
        boolean startFromFirst = mPreferencesHelper.getIsStartFromFirst();
        mLineId = startFromFirst ? mNormalLineId : mReverseLineId;
        fromStation = startFromFirst ? firstStation : lastStation;
    }

    private boolean getStartFrom() {
        return mPreferencesHelper.getIsStartFromFirst();
    }


    private void initAutoComplete(AutoCompleteTextView autoCompleteTextView) {
        mAutoCompleteAdapter = new LimitArrayAdapter<>(getActivity(), R.layout.item_auto_complete, autoCompleteViewModel.lineNameItems);
        autoCompleteTextView.setAdapter(mAutoCompleteAdapter);
        autoCompleteViewModel.setText(mPreferencesHelper.getLastQueryLine());
        refreshAutoComplete();
    }

    private void refreshAutoComplete() {
        //don't know why didn't auto refresh
        mSubscriptions.add(mPreferencesHelper.getAutoCompleteAsObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Set<String>>() {
                    @Override
                    public void onCompleted() {
                        Logger.i("auto onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        Logger.i(" auto e");

                    }

                    @Override
                    public void onNext(Set<String> strings) {
                        // TODO: 2016/3/3 0003 move to view model 
//                        Logger.i("auto onNext" + strings.toString());
                        mAutoCompleteAdapter.clear();
                        mAutoCompleteAdapter.addAll(strings);
                        mAutoCompleteAdapter.notifyDataSetChanged();
                    }
                }));
    }

    private void saveAutoComplete() {
//        Logger.i("save auto");
        mPreferencesHelper.saveAutoCompleteItem(mLineName);
    }

    private boolean getAutoRefresh() {
        return mPreferencesHelper.getAutoRefresh();
    }

    private void saveAutoRefresh(boolean isAutoRefresh) {
        mPreferencesHelper.saveAutoRefresh(isAutoRefresh);
    }

    private void initSwipeRefresh(SwipeRefreshLayout swipeRefreshLayout) {
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadBusIfNetworkConnected();
            }
        });
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);
    }

    private void initDependencyInjector() {
        ((MainActivity) getActivity()).trackComponent().inject(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSubscriptions = RxUtils.getNewCompositeSubIfUnsubscribed(mSubscriptions);
    }


    @Override
    public void onPause() {
        super.onPause();
        RxUtils.unsubscribeIfNotNull(mSubscriptions);
    }

    private void initToolbar(Toolbar toolbar) {
        setHasOptionsMenu(true);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.auto_refresh:
                        saveAutoRefresh(!item.isChecked());
                        item.setChecked(!item.isChecked());
                        executeAutoRefresh();
                        break;
                }
                return true;
            }
        });
    }


    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.auto_refresh).setChecked(getAutoRefresh());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.track_menu, menu);
    }

    private void setIsLoading() {
        trackViewModel.setIsLoading(false);
    }


    private void setIsOffline(boolean isOffline) {
        trackViewModel.setIsOffline(isOffline);
    }
}

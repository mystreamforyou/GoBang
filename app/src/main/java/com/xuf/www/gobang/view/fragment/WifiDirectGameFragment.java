package com.xuf.www.gobang.view.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;

import com.bluelinelabs.logansquare.LoganSquare;
import com.peak.salut.SalutDevice;
import com.squareup.otto.Subscribe;
import com.xuf.www.gobang.R;
import com.xuf.www.gobang.bean.Message;
import com.xuf.www.gobang.bean.Point;
import com.xuf.www.gobang.presenter.wifi.IWifiView;
import com.xuf.www.gobang.presenter.wifi.WifiPresenter;
import com.xuf.www.gobang.util.EventBus.RestartGameAckEvent;
import com.xuf.www.gobang.util.EventBus.WifiBeginWaitingEvent;
import com.xuf.www.gobang.util.EventBus.WifiCancelCompositionEvent;
import com.xuf.www.gobang.util.EventBus.WifiCancelWaitingEvent;
import com.xuf.www.gobang.util.EventBus.WifiConnectPeerEvent;
import com.xuf.www.gobang.util.EventBus.WifiCreateGameEvent;
import com.xuf.www.gobang.util.EventBus.WifiJoinGameEvent;
import com.xuf.www.gobang.util.EventBus.WifiCancelPeerEvent;
import com.xuf.www.gobang.util.GameJudger;
import com.xuf.www.gobang.util.MessageWrapper;
import com.xuf.www.gobang.util.ToastUtil;
import com.xuf.www.gobang.view.dialog.DialogCenter;
import com.xuf.www.gobang.widget.GoBangBoard;

import java.io.IOException;
import java.util.List;

/**
 * Created by Xuf on 2016/1/23.
 */
public class WifiDirectGameFragment extends BaseGameFragment implements IWifiView, GoBangBoard.PutChessListener
        , View.OnTouchListener, View.OnClickListener {

    private boolean mIsHost;
    private boolean mIsMePlay = false;
    private boolean mIsGameEnd = false;

    private WifiPresenter mWifiPresenter;

    private DialogCenter mDialogCenter;
    private GoBangBoard mBoard;

    private Button mRestart;
    private Button mExitGame;
    private Button mMoveBack;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unInit();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        initView(view);

        return view;
    }

    private void initView(View view) {
        mBoard = (GoBangBoard) view.findViewById(R.id.go_bang_board);
        mBoard.setOnTouchListener(this);
        mBoard.setPutChessListener(this);

        mRestart = (Button) view.findViewById(R.id.btn_restart);
        mRestart.setOnClickListener(this);

        mExitGame = (Button) view.findViewById(R.id.btn_exit);
        mExitGame.setOnClickListener(this);

        mMoveBack = (Button) view.findViewById(R.id.btn_move_back);
        mMoveBack.setOnClickListener(this);
    }

    private void init() {
        mDialogCenter = new DialogCenter(getActivity());
        mDialogCenter.showCompositionDialog();
        mWifiPresenter = new WifiPresenter(getActivity(), this);
        mWifiPresenter.initWifiNet();
    }

    private void unInit() {
        mWifiPresenter.unInitWifiNet(mIsHost);
    }

    private void reset() {
        mBoard.clearBoard();
        mIsMePlay = mIsHost;
        mIsGameEnd = false;
    }

    private void sendMessage(Message message) {
        if (mIsHost) {
            mWifiPresenter.sendToDevice(message);
        } else {
            mWifiPresenter.sendToHost(message);
        }
    }

    @Override
    public void onWifiInitFailed(String message) {
        ToastUtil.showShort(getActivity(), message);
        getActivity().finish();
    }

    @Override
    public void onDeviceConnected(SalutDevice device) {
        ToastUtil.showShort(getActivity(), "onDeviceConnected");
        mDialogCenter.enableWaitingPlayerDialogsBegin();
    }

    @Override
    public void onStartWifiServiceFailed() {
        ToastUtil.showShort(getActivity(), "onStartWifiServiceFailed");
    }

    @Override
    public void onFindPeers(List<SalutDevice> deviceList) {
        mDialogCenter.updatePeers(deviceList);
    }

    @Override
    public void onPeersNotFound() {
        ToastUtil.showShort(getActivity(), "found no peers");
    }

    @Override
    public void onDataReceived(Object o) {
        String str = (String) o;
        try {
            Message message = LoganSquare.parse(str, Message.class);
            int type = message.mMessageType;
            switch (type) {
                case Message.MSG_TYPE_HOST_BEGIN:
                    //joiner
                    mDialogCenter.dismissPeersAndComposition();
                    Message ack = MessageWrapper.getHostBeginAckMessage();
                    sendMessage(ack);
                    ToastUtil.showShort(getActivity(), "游戏开始");
                    break;
                case Message.MSG_TYPE_BEGIN_ACK:
                    //host
                    mDialogCenter.dismissWaitingAndComposition();
                    mIsMePlay = true;
                    break;
                case Message.MSG_TYPE_GAME_DATA:
                    mBoard.putChess(message.mIsWhite, message.mGameData.x, message.mGameData.y);
                    mIsMePlay = true;
                    break;
                case Message.MSG_TYPE_GAME_END:
                    ToastUtil.showShortDelay(getActivity(), message.mMessage, 1000);
                    mIsMePlay = false;
                    mIsGameEnd = true;
                    break;
                case Message.MSG_TYPE_GAME_RESTART_REQ:
                    if (mIsGameEnd) {
                        Message resp = MessageWrapper.getGameRestartRespMessage(true);
                        sendMessage(resp);
                        reset();
                    } else {
                        mDialogCenter.showRestartAckDialog();
                    }
                    break;
                case Message.MSG_TYPE_GAME_RESTART_RESP:
                    if (message.mAgreeRestart) {
                        reset();
                        ToastUtil.showShort(getActivity(), "游戏开始");
                    } else {
                        ToastUtil.showShort(getActivity(), "对方不同意重新开始游戏");
                    }
                    mDialogCenter.dismissRestartWaitingDialog();
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSendMessageFailed() {
        ToastUtil.showShort(getActivity(), "send message failed");
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_restart:
                Message message = MessageWrapper.getGameRestartReqMessage();
                sendMessage(message);
                mDialogCenter.showRestartWaitingDialog();
                break;
            case R.id.btn_move_back:
                break;
            case R.id.btn_exit:
                break;
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mIsMePlay) {
                    float x = motionEvent.getX();
                    float y = motionEvent.getY();
                    Point point = mBoard.convertPoint(x, y);
                    Message data = MessageWrapper.getSendDataMessage(point, mIsHost);
                    sendMessage(data);
                    mBoard.putChess(mIsHost, point.x, point.y);
                    mIsMePlay = false;
                }
                break;
        }
        return false;
    }

    @Override
    public void onPutChess(int[][] board, int x, int y) {
        if (mIsMePlay && GameJudger.isGameEnd(board, x, y)) {
            ToastUtil.showShort(getActivity(), "你赢了");
            Message end = MessageWrapper.getGameEndMessage("你输了");
            sendMessage(end);
            mIsMePlay = false;
            mIsGameEnd = true;
        }
    }

    @Subscribe
    public void onCreateGame(WifiCreateGameEvent event) {
        mIsHost = true;
        mWifiPresenter.startWifiService();
        mDialogCenter.showWaitingPlayerDialog();
    }

    @Subscribe
    public void onJoinGame(WifiJoinGameEvent event) {
        mIsHost = false;
        mWifiPresenter.findPeers();
        mDialogCenter.showPeersDialog();
    }

    @Subscribe
    public void onCancelCompositionDialog(WifiCancelCompositionEvent event) {
        getActivity().finish();
    }

    @Subscribe
    public void onConnectPeer(WifiConnectPeerEvent event) {
        mWifiPresenter.connectToHost(event.mDevice);
    }

    @Subscribe
    public void onCancelConnectPeer(WifiCancelPeerEvent event) {
        mDialogCenter.dismissPeersDialog();
    }

    @Subscribe
    public void onBeginGame(WifiBeginWaitingEvent event) {
        Message begin = MessageWrapper.getHostBeginMessage();
        sendMessage(begin);
    }

    @Subscribe
    public void onCancelWaitingDialog(WifiCancelWaitingEvent event) {
        mDialogCenter.dismissWaitingPlayerDialog();
    }

    @Subscribe
    public void onRestartAck(RestartGameAckEvent event) {
        Message ack = MessageWrapper.getGameRestartRespMessage(event.mAgreeRestart);
        sendMessage(ack);
        if (event.mAgreeRestart) {
            reset();
        }
        mDialogCenter.dismissRestartAckDialog();
    }
}
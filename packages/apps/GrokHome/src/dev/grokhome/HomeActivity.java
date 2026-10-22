package dev.grokhome;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.app.role.RoleManager;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Rotating launcher with foreground-only Grok voice. */
public final class HomeActivity extends Activity {
    private FrameLayout root, overlay, chatLayer;
    private LinearLayout drawer;
    private BotView bot;
    private AmbientWall wall;
    private SharedPreferences prefs;
    private BotConversation conversation;
    private AppUpdater appUpdater;
    private java.io.File pendingInstall;
    private NativeActivityFeed nativeFeed;
    private BotOverviewFeed overviewFeed;
    private BotActivityState activityState;
    private ConnectionState connection;
    private ConnectionMonitor connectionMonitor;
    private ConnectionChip connectionChip;
    private ComputerSheet computerSheet;
    private boolean stageDocked;
    private SetupPanel setupPanel;
    private SetupProgress setup;
    private Button micSettings;
    private TextView pinchHint;
    private int pinchStatusMargin = -1;
    private boolean returnToSetup, setupBehindChat, micPromptDenied;
    private final Map<String,TextView> chooserBadges=new HashMap<>();
    private org.json.JSONObject nativeSnapshot;
    private boolean nativeWorking,nativeStale=true;
    private TextChatPanel chatPanel;
    private View homeStatus,homeControls;
    private int chatImeBottom,chatSoftInputMode;
    private Button botChooser;
    private StatusBlock statusBlock;
    private final TaskProgress taskProgress=new TaskProgress();
    private RaiseHandController hands;
    private String progressBot="";
    private boolean ambient,ambientOverview,awaitingRoute;
    private String pendingTaskBot="",pendingTask="";
    private boolean pendingRoute;
    private final Map<String,String> taskPhases=new HashMap<>(),taskDetails=new HashMap<>();
    private final Map<String,Boolean> observedWorking=new HashMap<>();
    private final Map<String,Long> taskStarted=new HashMap<>();
    private final Runnable ambientTimer=()->enterAmbient(false);
    private final Runnable ambientListen=this::listenAmbient;
    private final Runnable taskTick=new Runnable(){public void run(){if(!resumed)return;renderTask();handler.postDelayed(this,1000);}};
    private String selectedBot="";
    private boolean switchingBot;
    private DemoMode demo;
    private String pendingVoiceBot="";
    private Runnable pendingVoiceStart;
    private HomeControls controls;
    private boolean resumed, destroyed, pendingListen;
    private String voiceTitle="Ready when you are", voiceDetail="Tap microphone to speak";
    private final BroadcastReceiver screenOff=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){clearTemporaryMute();exitAmbient(false);handler.removeCallbacks(ambientTimer);handler.removeCallbacks(ambientListen);cancelVoiceHandoff();if(conversation!=null)conversation.pause();if(nativeFeed!=null)nativeFeed.stop();if(overviewFeed!=null)overviewFeed.stop();if(activityState!=null){activityState.clearWorking();refreshActivity();}}
            else if(Intent.ACTION_USER_PRESENT.equals(i.getAction())){if(resumed && !selectedBot.isEmpty()){conversation.resume();nativeFeed.start(voiceServer(),selectedBot);overviewFeed.start(voiceServer());}resetAmbientTimer();maybeAutoListen();armWake();}
        }
    };
    private float downX, downY;
    private int panelWidth;
    private Runnable overlayLayout;
    private boolean closingChat,touchWasMulti;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int size) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(Color.WHITE); return v;
    }
    private GradientDrawable surface(int color, int radius) { return surface(color, radius, false); }
    private GradientDrawable surface(int color, int radius, boolean interactive) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), interactive ? getColor(R.color.border_interactive) : 0xff34383e); return d;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(15);
        b.setMinHeight(dp(48)); b.setPadding(dp(18), dp(4), dp(18), dp(4)); b.setBackground(surface(0xff17191c, 28, true));
        b.setOnClickListener(v -> action.run()); return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); prefs = getSharedPreferences("home", MODE_PRIVATE);
        activityState=new BotActivityState(prefs);
        connection=new ConnectionState();
        connectionMonitor=new ConnectionMonitor(this,connection,this::voiceServer);
        setup=SetupProgress.load(prefs.contains("voice_server"),prefs.getBoolean("setup_started",false),prefs.getInt("setup_done",0),prefs.getInt("setup_skipped",0),prefs.getBoolean("setup_dismissed",false),prefs.getBoolean("setup_home_attempted",false));
        if(setup.existingInstall)saveSetup();
        appUpdater=new AppUpdater(this);
        SharedPreferences.Editor identities=prefs.edit();
        for(int i=0;i<BotIdentity.NAMES.length;i++){String key="bot_color_"+BotIdentity.NAMES[i];if(!prefs.contains(key))identities.putInt(key,BotIdentity.COLORS[i]);}
        identities.apply();
        // Apply the owner's chosen identity once; subsequent color-picker changes still persist.
        if(!prefs.getBoolean("max_red_identity",false))prefs.edit().putInt("bot_color_Max",BallColors.RED).putBoolean("max_red_identity",true).apply();
        if(!prefs.getBoolean("kit_white_identity",false))prefs.edit().putInt("bot_color_Nova",BallColors.WHITE).putBoolean("kit_white_identity",true).apply();
        if(!prefs.getBoolean("orion_grey_identity",false))prefs.edit().putInt("bot_color_Orion",0xff777777).putBoolean("orion_grey_identity",true).apply();
        // 2026-10-19: Max takes his new white color once. Every other bot keeps whatever color is saved; the color picker still persists later changes.
        if(!prefs.getBoolean("max_white_identity",false))prefs.edit().putInt("bot_color_Max",BallColors.WHITE).putBoolean("max_white_identity",true).apply();
        if(!prefs.getBoolean("wake_mic_v1",false))prefs.edit().putBoolean("wake_mic_v1",true).putBoolean("muted",false).apply();
        if(!prefs.getBoolean("hey_bots_v1",false))prefs.edit().putBoolean("hey_bots_v1",true).putBoolean("muted",false).apply();
        ListenPreference.migrate(prefs);
        selectedBot=prefs.getString("selected_bot",BotIdentity.PRIMARY);
        if(selectedBot.isEmpty()){selectedBot=BotIdentity.PRIMARY;prefs.edit().putString("selected_bot",selectedBot).apply();}
        progressBot=selectedBot;
        if(!selectedBot.isEmpty())voiceDetail="Talk to "+selectedBot;
        conversation=new BotConversation(this,new BotConversation.Listener(){
            public void state(String title,String detail){if(!destroyed && (ConnectionRules.OFFLINE_TITLE.equals(title) || "Connection interrupted".equals(title)))connectionMonitor.noteUnreachable();if(!destroyed && !selectedBot.isEmpty()){voiceTitle=title;voiceDetail=detail;updateStatus();if(ambient && !title.equals("Listening")){handler.removeCallbacks(ambientListen);handler.postDelayed(ambientListen,2500);}armWake();}}
            public void speaking(boolean value){if(destroyed || bot==null || selectedBot.isEmpty())return;if(value && bot.isOverview()){ambientOverview=false;bot.setOverview(false);}if(value)conversation.stopWake();else armWake();bot.setSpeaking(value);}
            public void armed(){if(destroyed)return;if("Listening".equals(voiceTitle) || "One moment".equals(voiceTitle))voiceTitle="Ready when you are";if("Ready when you are".equals(voiceTitle))updateStatus();}
            public void wake(String remainder){onWake(remainder);}
            public void heard(String spoken){if(!destroyed && spoken!=null && !spoken.isEmpty() && !conversation.micHeld()){voiceTitle="Ready when you are";voiceDetail="Heard \""+spoken+"\"";updateStatus();}}
            public void transcript(String role,String value){if(!destroyed)saveTranscript(role,value);}
            public boolean command(String spoken){return handleVoiceCommand(spoken);}
            public void routed(String name,String request){switchWithTask(name,request,false);}
            public void task(String phase,String detail){if(phase.equals("working") && ("stopping".equals(taskPhases.get(selectedBot)) || "stop_failed".equals(taskPhases.get(selectedBot)))){renderTask();return;}taskPhases.put(selectedBot,phase);if(!TaskProgress.genericProgress(detail))taskDetails.put(selectedBot,detail);else taskDetails.putIfAbsent(selectedBot,detail==null?"":detail);applyPhase(phase,detail);if(hands!=null)hands.observeLocal(selectedBot,phase,conversation.busy());renderTask();if(!phase.equals("working") && !phase.equals("stopping") && selectedBot.equals(pendingTaskBot) && !pendingTask.isEmpty() && !conversation.busy())botSwitchReady(selectedBot);}
            public void taskStart(long epochMillis){noteWorkingClock(selectedBot,epochMillis);renderTask();}
        });
        conversation.select(voiceServer(),selectedBot);
        nativeFeed=new NativeActivityFeed(snapshot->{
            if(destroyed || !resumed || !selectedBot.equals(snapshot.optString("bot")))return;
            observeTask(snapshot);activityState.update(snapshot);trackHands(snapshot);refreshActivity();
            nativeStale=snapshot.optBoolean("stale");nativeWorking=!nativeStale && snapshot.optBoolean("working");
            if(!nativeStale){nativeSnapshot=snapshot;connectionMonitor.noteSynced(SystemClock.elapsedRealtime());}
            updateStatus();if(chatPanel!=null && !nativeStale)chatPanel.setNativeHistory(snapshot.optJSONArray("entries"));
        });
        overviewFeed=new BotOverviewFeed(snapshot->{if(!destroyed && resumed){if(!snapshot.optBoolean("stale"))connectionMonitor.noteSynced(SystemClock.elapsedRealtime());observeTask(snapshot);activityState.update(snapshot);trackHands(snapshot);refreshActivity();}});
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK); setContentView(root);
        bot = new BotView(this); root.addView(bot, new FrameLayout.LayoutParams(-1,-1));
        bot.setBotName(selectedBot);
        bot.setBallColor(prefs.getInt(colorKey(),BallColors.WHITE));
        bot.restoreOverview(prefs.getBoolean("stage_overview",false));
        wall=new AmbientWall(this);wall.setVisibility(View.GONE);wall.setOpen(this::openAmbientBot);wall.setNeedsYouOpen(this::showRaiseHandCard);root.addView(wall,new FrameLayout.LayoutParams(-1,-1));
        botChooser=button(selectedBot.isEmpty()?"Bots ▾":selectedBot+" ▾",this::showBotChooser);
        botChooser.setContentDescription("Choose bot");
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.LEFT);bp.setMargins(dp(24),dp(24),dp(24),0);root.addView(botChooser,bp);
        connectionChip=new ConnectionChip(this,this::showVoiceConnection);
        FrameLayout.LayoutParams chipParams=new FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.RIGHT);chipParams.setMargins(dp(24),dp(24),dp(24),0);root.addView(connectionChip,chipParams);
        statusBlock=new StatusBlock(this);
        connection.observe(s->{if(!destroyed)applyConnection(s);});
        micSettings=button("Open settings",this::openAppSettings);micSettings.setVisibility(View.GONE);
        statusBlock.addView(micSettings,new LinearLayout.LayoutParams(-2,dp(48)));
        controls = new HomeControls(this, new HomeControls.Callbacks() {
            public void onApps(){showApps();}
            public void onType(){showKeyboard();}
            public void onHistory(){showHistory();}
            public void onSettings(){showDrawer();}
            public void onMicTap(){toggleMicrophone();}
            public void onMicLongPress(){muteFromLongPress();}
        });
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(statusBlock,sp);homeStatus=statusBlock;
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(HomeBand.ROW_HEIGHT_DP),Gravity.BOTTOM);root.addView(controls,cp);homeControls=controls;
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol || b-t!=ob-ot)layoutBand();});
        statusBlock.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot)layoutBand();});
        bot.setOnClickListener(v -> {cancelVoiceHandoff();if(conversation.listening() || conversation.micHeld() || voiceTitle.endsWith("is speaking"))conversation.interruptSpeech();});
        bot.setNavigation((name,direction)->{
            String target=!bot.isOverview() && direction!=0?BotIdentity.NAMES[Math.floorMod(BotIdentity.index(selectedBot)+direction,BotIdentity.NAMES.length)]:name;
            selectBot(target,BotIdentity.color(target),direction);
        });
        hands=new RaiseHandController(this,prefs,new RaiseHandController.Host(){
            public void openChat(String name){if(name!=null && !name.equals(selectedBot))selectBot(name,BotIdentity.color(name));showKeyboard(false,false);}
            public void setCardOpen(boolean open){setRaiseCardOpen(open);}
            public boolean quiet(long now){
                boolean enabled=prefs.getBoolean(QuietHours.ENABLED,true);
                int start=prefs.getInt(QuietHours.START,QuietHours.DEFAULT_START);
                int end=prefs.getInt(QuietHours.END,QuietHours.DEFAULT_END);
                return QuietHours.active(enabled,start,end,QuietHours.minuteOfDay(now,java.time.ZoneId.systemDefault()));
            }
            public boolean reduced(){return reducedMotion();}
            public boolean offline(){return computerOffline();}
            public void announce(String sentence){if(bot!=null && sentence!=null && !sentence.isEmpty())bot.announceForAccessibility(sentence);}
            public int color(String name){return prefs.getInt("bot_color_"+name,BotIdentity.color(name));}
            public void onOverview(boolean overview){if(bot!=null)bot.setOverview(overview);}
        });
        wall.setNeedsYou(raiseHandStage());
        hands.attach(root);
        demo=new DemoMode(hands.stage(),activityState,()->{if(destroyed)return;refreshActivity();if(bot!=null)hands.push(bot);});
        bot.setHandListener(this::openRaisedHand);
        if((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0)hands.applyIntent(getIntent(),bot);
        applyDemoIntent(getIntent());
        root.setOnApplyWindowInsetsListener((view,insets)->{chatImeBottom=insets.getInsets(WindowInsets.Type.ime()).bottom;if(chatPanel!=null)layoutChat();return insets;});
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol || b-t!=ob-ot){if(chatPanel!=null)layoutChat();else if(overlayLayout!=null)overlayLayout.run();}});
        if(saved!=null?saved.getBoolean("text_chat"):prefs.getBoolean("chat_open",false))root.post(()->showKeyboard(false,false));
        else if(saved!=null && saved.getBoolean("drawer")) root.post(this::showDrawer);
        else if(setup.showOnLaunch())root.post(this::showSetup);
        updateStatus(); immersive();
    }
    private void immersive() {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController c=getWindow().getInsetsController();
        if(c!=null){c.hide(WindowInsets.Type.systemBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){
        super.onConfigurationChanged(configuration);
        // Keep the conversation, composer, open menus and dialogs alive during rotation.
        immersive();root.requestApplyInsets();root.requestLayout();
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;bot.setActive(true);
        if(pendingInstall!=null && getPackageManager().canRequestPackageInstalls())openUpdateInstaller();
        nativeFeed.start(voiceServer(),selectedBot);overviewFeed.start(voiceServer());if(prefs.contains("voice_server"))connectionMonitor.start();
        if(!selectedBot.isEmpty())conversation.resume();
        IntentFilter signals=new IntentFilter(Intent.ACTION_SCREEN_OFF);signals.addAction(Intent.ACTION_USER_PRESENT);
        if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(screenOff,signals,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff,signals);
        if(pendingListen && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){pendingListen=false;startListening();}
        else maybeAutoListen();refreshSetupGates();armWake();updateStatus();resetAmbientTimer();handler.removeCallbacks(taskTick);handler.post(taskTick);
        if(wall!=null)wall.invalidate();
    }
    @Override protected void onPause(){
        exitAmbient(false);handler.removeCallbacks(ambientTimer);handler.removeCallbacks(ambientListen);handler.removeCallbacks(taskTick);pendingTask="";pendingTaskBot="";awaitingRoute=false;cancelVoiceHandoff();saveChatPlace();resumed=false;nativeFeed.stop();overviewFeed.stop();connectionMonitor.stop();activityState.clearWorking();refreshActivity();unregisterReceiver(screenOff);conversation.pause();bot.setActive(false);super.onPause();
    }
    @Override protected void onStop(){
        clearTemporaryMute();
        super.onStop();
    }
    @Override protected void onDestroy(){destroyed=true;if(demo!=null)demo.release();appUpdater.close();connectionMonitor.close();nativeFeed.close();overviewFeed.close();conversation.close();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("drawer",drawer!=null);out.putBoolean("text_chat",chatPanel!=null);super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(hands!=null && (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0)hands.applyIntent(intent,bot);
        applyDemoIntent(intent);
        // A second HOME intent reaches a live singleTask launcher here, not onCreate.
        if(isHomeLaunch(intent))presentSetupOnHome();
        else if(overlay!=null)removeOverlay();
        maybeAutoListen();
        armWake();
    }
    private boolean isHomeLaunch(Intent intent){
        return intent!=null && Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME);
    }
    private void reloadSetup(){
        setup=SetupProgress.load(prefs.contains("voice_server"),prefs.getBoolean("setup_started",false),prefs.getInt("setup_done",0),prefs.getInt("setup_skipped",0),prefs.getBoolean("setup_dismissed",false),prefs.getBoolean("setup_home_attempted",false));
    }
    /** Home shows the checklist whenever the saved setup is still unfinished. */
    private void presentSetupOnHome(){
        reloadSetup();
        refreshSetupGates();
        if(setup!=null && setup.showOnLaunch()){
            if(setupPanel==null)showSetup();
            else bindSetup();
            return;
        }
        if(overlay!=null)removeOverlay();
    }
    private String voiceServer(){
        boolean emulator=Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish") || Build.MODEL.startsWith("sdk_gphone");
        return prefs.getString("voice_server",emulator?"http://127.0.0.1:8787":"http://127.0.0.1:8787");
    }
    private boolean unlocked(){return !((android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();}
    private void maybeAutoListen(){
        // The Listen for Hey Bots switch arms wake. It does not open a command session.
        armWake();
    }
    private boolean temporarilyMuted(){return prefs.getBoolean("muted",false);}
    /** Session-only. Screen off and onStop clear it so the next visit starts listening. */
    private void clearTemporaryMute(){
        if(!temporarilyMuted())return;
        ListenPreference.setMuted(prefs,false);
        if(statusBlock!=null)updateStatus();
    }
    private MicState.Phase currentMicPhase(){
        boolean recording=conversation.listening() || conversation.micHeld();
        boolean permission=checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        return MicState.phase(recording,ListenPreference.switchOn(prefs),temporarilyMuted(),permission);
    }
    private void toggleMicrophone(){
        cancelVoiceHandoff();
        MicState.Tap tap=MicState.onTap(currentMicPhase());
        switch(tap){
            case START:
                ListenPreference.setMuted(prefs,false);
                if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
                    pendingListen=true;
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},7);return;
                }
                startListening();
                break;
            case STOP:
                conversation.interruptSpeech();
                armWake();
                updateStatus();
                break;
            default:
                MicState.Tap unknown=tap;
                throw new IllegalStateException("Unhandled mic tap "+unknown);
        }
    }
    private void muteFromLongPress(){
        boolean muted=MicState.mutedAfterLongPress(prefs.getBoolean("muted",false));
        ListenPreference.setMuted(prefs,muted);
        cancelVoiceHandoff();
        if(muted){
            conversation.stopAudio();
            if("Listening".equals(voiceTitle) || voiceTitle.endsWith("is speaking"))voiceTitle="Ready when you are";
        }else armWake();
        updateStatus();
    }
    private void armWake(){
        if(destroyed || !resumed || !unlocked() || chatPanel!=null || !ListenPreference.wakeEnabled(prefs))return;
        if(voiceTitle.endsWith("is speaking") || conversation.listening() || conversation.micHeld() || conversation.wakeArmed())return;
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return;
        conversation.listenForWake();
        syncWakeHint();
    }
    private void onWake(String remainder){
        if(destroyed || chatPanel!=null || !ListenPreference.wakeEnabled(prefs))return;
        resetAmbientTimer();if(ambient)exitAmbient(false);
        String rest=remainder==null?"":remainder.trim();
        if(rest.isEmpty()){
            voiceTitle="Listening";voiceDetail="Speak to Max";
            requestVoiceHandoff(BotIdentity.PRIMARY);
            if(!BotIdentity.PRIMARY.equals(selectedBot)){if(bot.isOverview())bot.centerBot(BotIdentity.PRIMARY);else{bot.touch();selectBot(BotIdentity.PRIMARY,BotIdentity.color(BotIdentity.PRIMARY));}updateStatus();}
            else{bot.touch();startListening();}
            return;
        }
        if(handleVoiceCommand(rest))return;
        if(!BotIdentity.PRIMARY.equals(selectedBot))switchWithTask(BotIdentity.PRIMARY,rest,false);
        else conversation.sendVoice(rest);
    }
    private void startListening(){
        if(!resumed || !unlocked())return;
        ListenPreference.setMuted(prefs,false);
        conversation.listen();
        updateStatus();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==7 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED){pendingListen=true;micPromptDenied=false;showMicSettings(false);if(setup!=null && !setup.existingInstall){setup=setup.complete(SetupProgress.MIC);saveSetup();}if(resumed){pendingListen=false;startListening();}if(setupPanel!=null)bindSetup();}
        else if(request==7){pendingListen=false;micPromptDenied=true;showMicSettings(true);voiceTitle="Microphone permission needed";voiceDetail="Turn on the microphone in Android settings, or use the keyboard.";updateStatus();if(setupPanel!=null)bindSetup();}
    }
    private void updateStatus(){
        if(statusBlock==null)return;refreshActivity();renderTask();
        if(setupPanel!=null && !micPromptDenied){
            if(setup.complete()){voiceTitle="You're all set";voiceDetail="Tap Finish to meet the whole team";}
            else{voiceTitle="Hi, I'm Max";voiceDetail="Let's connect your team.";}
            statusBlock.showIdle(voiceTitle,voiceDetail);
        }
        syncWakeHint();
    }
    private void syncWakeHint(){
        if(wall!=null)wall.setWakeListening(conversation!=null && conversation.wakeArmed());
    }
    private void cancelVoiceHandoff(){
        pendingVoiceBot="";if(pendingVoiceStart!=null){handler.removeCallbacks(pendingVoiceStart);pendingVoiceStart=null;}
    }
    private void requestVoiceHandoff(String name){cancelVoiceHandoff();pendingVoiceBot=name;}
    private void queueVoiceHandoff(){
        if(pendingVoiceBot.isEmpty() || !pendingVoiceBot.equals(selectedBot) || switchingBot || !resumed)return;
        if(pendingVoiceStart!=null)handler.removeCallbacks(pendingVoiceStart);
        final String expected=pendingVoiceBot;
        // Let the previous recognition callback/session finish after the avatar settles.
        pendingVoiceStart=()->{
            if(!expected.equals(pendingVoiceBot))return;
            cancelVoiceHandoff();
            if(!destroyed && resumed && unlocked() && !switchingBot && expected.equals(selectedBot)
                && !prefs.getBoolean("muted",false) && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
                && !conversation.listening())startListening();
        };
        handler.postDelayed(pendingVoiceStart,250);
    }
    private void botSwitchReady(String name){if(resumed && !name.isEmpty()){
        conversation.resume();
        boolean deciding=false;
        if(name.equals(pendingTaskBot) && !pendingTask.isEmpty()){
            if(conversation.busy()){voiceTitle=name+" is working";voiceDetail="Sending your request when this reply finishes";updateStatus();}
            else{String message=pendingTask;boolean route=pendingRoute;pendingTask="";pendingTaskBot="";pendingRoute=false;deciding=route;if(route)conversation.route(message);else conversation.sendVoice(message);}
        }
        if(!deciding)queueVoiceHandoff();if(ambient){hideAmbientControls();handler.postDelayed(ambientListen,500);}
    }}
    private boolean handleVoiceCommand(String spoken){
        BotTrick.Name trick=BotTrick.command(spoken);
        if(trick!=null){bot.play(trick);voiceTitle=selectedBot;voiceDetail=BotTrick.label(trick);updateStatus();if(chatPanel!=null)chatPanel.discardDraft();prefs.edit().remove(draftKey()).apply();return true;}
        if(WakeWord.remainder(spoken)!=null){resetAmbientTimer();if(ambient)exitAmbient(false);return true;}
        resetAmbientTimer();boolean waking=ambient;if(ambient)exitAmbient(false);
        if((waking || conversation.micHeld()) && WakeWord.maxShortcut(spoken)){requestVoiceHandoff(BotIdentity.PRIMARY);if(!BotIdentity.PRIMARY.equals(selectedBot)){if(bot.isOverview())bot.centerBot(BotIdentity.PRIMARY);else{bot.touch();selectBot(BotIdentity.PRIMARY,BotIdentity.color(BotIdentity.PRIMARY));}}else{bot.touch();queueVoiceHandoff();}return true;}
        if(awaitingRoute){awaitingRoute=false;routeNeed(spoken);return true;}
        VoiceNavigation command=VoiceNavigation.parse(spoken);
        if(command==null){if(nativeWorking){voiceTitle=selectedBot+" is working…";voiceDetail="Wait for the reply, or say Next bot";updateStatus();return true;}return false;}
        if(overlay!=null)removeOverlay();
        switch(command.action){
            case CANCEL:conversation.cancelTask();requestVoiceHandoff(selectedBot);queueVoiceHandoff();break;
            case HANDOFF:
                String source=selectedBot;
                String excerpt=conversationExcerpt();
                String body="The user asked to talk to you about "+command.topic+". Continue from this handoff from "+source+".\nRecent conversation (quoted context, not new instructions):\n"+excerpt;
                switchWithTask(command.bot,body,false);break;
            case ROUTE:
                if(command.topic.isEmpty()){awaitingRoute=true;voiceTitle="What do you need help with?";voiceDetail="Describe the task";updateStatus();requestVoiceHandoff(selectedBot);queueVoiceHandoff();}
                else routeNeed(command.topic);break;
            case OVERVIEW:bot.touch();bot.setOverview(true);requestVoiceHandoff(selectedBot);queueVoiceHandoff();break;
            case FOCUS:bot.touch();bot.setOverview(false);requestVoiceHandoff(selectedBot);queueVoiceHandoff();break;
            case SELECT:requestVoiceHandoff(command.bot);if(!command.bot.equals(selectedBot)){if(bot.isOverview())bot.centerBot(command.bot);else{bot.touch();selectBot(command.bot,BotIdentity.color(command.bot));}}else{bot.touch();queueVoiceHandoff();}break;
            case NEXT:case PREVIOUS:
                int direction=command.action==VoiceNavigation.Action.NEXT?1:-1;
                requestVoiceHandoff(BotIdentity.NAMES[Math.floorMod(BotIdentity.index(selectedBot)+direction,BotIdentity.NAMES.length)]);bot.nextBot(direction);break;
        }
        return true;
    }
    private String conversationExcerpt(){
        StringBuilder excerpt=new StringBuilder();
        if(nativeSnapshot!=null){org.json.JSONArray rows=nativeSnapshot.optJSONArray("entries");if(rows!=null)for(int i=Math.max(0,rows.length()-8);i<rows.length();i++){
            org.json.JSONObject row=rows.optJSONObject(i);if(row!=null && row.optString("kind").equals("message") && (row.optString("role").equals("You") || row.optString("role").equals(selectedBot)))excerpt.append(row.optString("role")).append(": ").append(row.optString("text")).append("\n");
        }}
        if(excerpt.length()==0)excerpt.append(prefs.getString(historyKey(),""));
        String text=excerpt.toString();return text.length()>2700?text.substring(text.length()-2700):text;
    }
    private void switchWithTask(String name,String message,boolean route){
        if(message.length()>3900)message=message.substring(0,3900);
        pendingTaskBot=name;pendingTask=message;pendingRoute=route;
        if(!route)requestVoiceHandoff(name);
        if(name.equals(selectedBot)){botSwitchReady(name);return;}
        if(bot.isOverview())bot.centerBot(name);else{bot.touch();selectBot(name,BotIdentity.color(name));}
    }
    private void routeNeed(String request){
        if(request.trim().isEmpty())return;
        if(activityState.working(BotIdentity.PRIMARY) || selectedBot.equals(BotIdentity.PRIMARY) && conversation.busy()){
            voiceTitle="Max is busy";voiceDetail="Choose a bot or try Who can help when Max finishes";updateStatus();return;
        }
        switchWithTask(BotIdentity.PRIMARY,request,true);
    }
    private void observeTask(org.json.JSONObject snapshot){
        String name=snapshot.optString("bot");if(snapshot.optBoolean("stale") || !snapshot.has("working") || snapshot.isNull("working"))return;
        if(needsYou(snapshot)){
            observedWorking.put(name,false);taskPhases.remove(name);
            if(name.equals(selectedBot))taskProgress.handoffNeedsYou(name);
            renderTask();return;
        }
        if(name.equals(selectedBot) && taskProgress.phase()==TaskProgress.Phase.NEEDS_YOU)taskProgress.idle();
        boolean working=snapshot.optBoolean("working");Boolean previous=observedWorking.put(name,working);
            if(working){
            String saved=prefs.getString("bot_task_"+name,"");String retired=prefs.getString("task_retired_"+name,"");String request=latestYou(snapshot);
            noteWorkingClock(name,0);
            boolean seen=prefs.getLong("bot_started_"+name,0L)<=0;
            String live=seen?request:(!saved.isEmpty()?saved:request);
            String chosen=seen?TaskProgress.seenDetail(saved,retired,live):live;
            if(!chosen.isEmpty())taskDetails.put(name,clip(chosen));
            else if(seen)taskDetails.remove(name);
            else taskDetails.putIfAbsent(name,"Working on your request");
            String current=taskPhases.get(name);
            if(!"stopping".equals(current) && !"stop_failed".equals(current)){taskPhases.put(name,"working");if(name.equals(selectedBot))taskProgress.working(name,detailFor(name));}
        }else if(Boolean.TRUE.equals(previous) && !suppressNativeFinish(taskPhases.get(name)) && !(name.equals(selectedBot) && conversation.busy())){
            taskPhases.put(name,"finished");
            clearRequest(name);
            if(name.equals(selectedBot))taskProgress.finished(name,"",System.currentTimeMillis());
        }
        renderTask();
    }
    private static boolean needsYou(org.json.JSONObject snapshot){org.json.JSONArray rows=snapshot.optJSONArray("needs_you");return rows!=null && rows.length()>0;}
    private static String latestYou(org.json.JSONObject snapshot){
        org.json.JSONArray rows=snapshot.optJSONArray("entries");if(rows==null)return "";
        for(int i=rows.length()-1;i>=0;i--){org.json.JSONObject row=rows.optJSONObject(i);if(row!=null && "You".equals(row.optString("role")))return row.optString("text");}
        return "";
    }
    private static boolean suppressNativeFinish(String phase){
        return "stopping".equals(phase) || "stopped".equals(phase) || "cancelled".equals(phase) || "stop_failed".equals(phase) || "finished".equals(phase) || "complete".equals(phase);
    }
    private static String clip(String value){if(value==null)return "";return value.substring(0,Math.min(120,value.length()));}
    private String detailFor(String name){
        String saved=prefs.getString("bot_task_"+name,"");String retired=prefs.getString("task_retired_"+name,"");String detail=taskDetails.get(name);
        if(taskProgress.seen() && (taskProgress.phase()==TaskProgress.Phase.WORKING || taskProgress.phase()==TaskProgress.Phase.IDLE))return TaskProgress.seenDetail(saved,retired,detail);
        if(taskProgress.phase()==TaskProgress.Phase.FINISHED || taskProgress.phase()==TaskProgress.Phase.STOPPED || taskProgress.phase()==TaskProgress.Phase.STOP_FAILED)return TaskProgress.seenDetail(saved,retired,detail);
        String request=clip(saved);
        if(!request.isEmpty() && TaskProgress.genericProgress(detail))return request;
        if(detail!=null && !TaskProgress.genericProgress(detail))return clip(detail);
        if(!request.isEmpty())return request;
        return detail==null || detail.isEmpty()?"Waiting for bot reply":detail;
    }
    private void clearRequest(String name){
        if(name==null || name.isEmpty())return;
        String saved=prefs.getString("bot_task_"+name,"");
        android.content.SharedPreferences.Editor edit=prefs.edit().remove("bot_task_"+name);
        if(!saved.isEmpty())edit.putString("task_retired_"+name,saved);
        edit.apply();
        String detail=taskDetails.get(name);
        if(TaskProgress.sameRequest(detail,saved) || TaskProgress.sameRequest(detail,prefs.getString("task_retired_"+name,"")))taskDetails.remove(name);
    }
    private void applyPhase(String phase,String detail){
        long now=System.currentTimeMillis();
        switch(phase){
            case "working" -> {if(!TaskProgress.genericProgress(detail))taskDetails.put(selectedBot,detail);noteWorkingClock(selectedBot,0);if(prefs.getLong("bot_started_"+selectedBot,0L)>0)prefs.edit().remove("task_retired_"+selectedBot).apply();taskProgress.working(selectedBot,detailFor(selectedBot));}
            case "stopping" -> taskProgress.stopping(selectedBot,detail);
            case "stopped","cancelled" -> {clearRequest(selectedBot);taskProgress.stopped(selectedBot,now);}
            case "stop_failed" -> {clearRequest(selectedBot);taskProgress.stopFailed(selectedBot,"");}
            case "finished","complete" -> {clearRequest(selectedBot);taskProgress.finished(selectedBot,"",now);}
            case "error" -> {}
            default -> {}
        }
    }
    private void noteWorkingClock(String name,long serverMillis){
        if(name==null || name.isEmpty())return;
        long now=System.currentTimeMillis();
        long stored=prefs.getLong("bot_started_"+name,0L),notice=prefs.getLong("task_notice_"+name,0L);
        TaskProgress.Clock clock=TaskProgress.resolve(now,serverMillis,stored,notice);
        if(clock.real){if(stored!=clock.originMillis || notice!=0)prefs.edit().putLong("bot_started_"+name,clock.originMillis).remove("task_notice_"+name).apply();}
        else if(notice<=0)prefs.edit().putLong("task_notice_"+name,clock.originMillis).apply();
        taskStarted.put(name,clock.originMillis);
        if(name.equals(selectedBot))taskProgress.setClock(clock);
    }
    private void clearClock(String name){
        if(name==null || name.isEmpty())return;
        prefs.edit().remove("bot_started_"+name).remove("task_notice_"+name).apply();
        taskStarted.remove(name);
        if(name.equals(selectedBot))taskProgress.clearClock();
    }
    private void ensureProgressBot(){
        if(selectedBot.equals(progressBot))return;
        String previous=progressBot;progressBot=selectedBot;
        if(taskProgress.phase()==TaskProgress.Phase.NEEDS_YOU && selectedBot.equals(taskProgress.bot()))return;
        if(!previous.isEmpty()){taskProgress.idle();taskProgress.clearClock();}
        String phase=taskPhases.get(selectedBot);if(phase!=null)applyPhase(phase,taskDetails.getOrDefault(selectedBot,""));
    }
    private static boolean taskVoice(String title){
        return "Task stopped".equals(title) || "Couldn't stop the task".equals(title) || title!=null && (title.endsWith(" is working") || title.endsWith(" is working…"));
    }
    private String idleSubtitle(){
        if("Ready when you are".equals(voiceTitle)){
            if(voiceDetail!=null && voiceDetail.startsWith("Heard "))return voiceDetail;
            return MicState.subtitle(currentMicPhase());
        }
        return voiceDetail==null?"":voiceDetail;
    }
    private void paintStatus(){
        long now=System.currentTimeMillis();
        statusBlock.setDetailAction(null);
        TaskProgress.Phase phase=taskProgress.phase();
        switch(phase){
            case WORKING -> statusBlock.showWorking(taskProgress.title(),taskProgress.clockLabel(now),taskProgress.seen(),detailFor(selectedBot));
            case STOPPING -> statusBlock.showTitle(taskProgress.title(),"");
            case STOPPED, STOP_FAILED, FINISHED -> statusBlock.showTitle(taskProgress.title(),"");
            case IDLE, NEEDS_YOU -> {
                if(needsYouLine()){
                    statusBlock.showIdle(selectedBot+" needs you","Tap the raised hand to answer");
                    statusBlock.setDetailAction(()->showRaiseHandCard(selectedBot));
                }else if(phase==TaskProgress.Phase.NEEDS_YOU && computerOffline() && !voiceBusy()){
                    statusBlock.showIdle(ConnectionRules.OFFLINE_TITLE,ConnectionRules.OFFLINE_SAVED);
                }else statusBlock.showIdle(voiceTitle,idleSubtitle());
            }
            default -> {TaskProgress.Phase unknown=phase;throw new IllegalStateException("Unhandled task phase "+unknown);}
        }
    }
    private void paintMic(){
        if(controls==null || conversation==null)return;
        controls.setPhase(currentMicPhase(),selectedBot,temporarilyMuted());
    }
    private void paintWings(){
        if(controls==null)return;
        TaskProgress.Phase phase=taskProgress.phase();
        switch(phase){
            case WORKING -> controls.setWings("Stop task",R.drawable.ic_stop,()->conversation.cancelTask(),"Open chat",R.drawable.ic_chat,this::showKeyboard);
            case STOPPING -> controls.setWings("Stop task",R.drawable.ic_stop,()->{},"Open chat",R.drawable.ic_chat,this::showKeyboard);
            case STOP_FAILED -> controls.setWings("Try again",0,()->conversation.cancelTask(),"Open chat",R.drawable.ic_chat,this::showKeyboard);
            case FINISHED -> controls.setWings("View reply",0,this::showKeyboard,"Dismiss",0,this::dismissTask);
            case IDLE, STOPPED, NEEDS_YOU -> controls.setWings("Type",R.drawable.ic_keyboard,this::showKeyboard,"History",R.drawable.ic_history,this::showHistory);
            default -> {TaskProgress.Phase unknown=phase;throw new IllegalStateException("Unhandled task phase "+unknown);}
        }
        controls.setLeftBusy(phase==TaskProgress.Phase.STOPPING);
    }
    private void dismissTask(){
        taskPhases.remove(selectedBot);clearRequest(selectedBot);taskDetails.remove(selectedBot);taskProgress.dismiss();clearClock(selectedBot);
        if(taskVoice(voiceTitle)){voiceTitle="Ready when you are";voiceDetail=selectedBot.isEmpty()?"Tap microphone to speak":"Talk to "+selectedBot;}
        renderTask();
    }
    private void renderTask(){
        if(hands!=null && bot!=null)hands.tick(bot);
        if(statusBlock==null || controls==null)return;
        ensureProgressBot();
        if(taskProgress.tick(System.currentTimeMillis())){
            taskPhases.remove(selectedBot);clearRequest(selectedBot);taskDetails.remove(selectedBot);clearClock(selectedBot);
            if(taskVoice(voiceTitle)){voiceTitle="Ready when you are";voiceDetail=selectedBot.isEmpty()?"Tap microphone to speak":"Talk to "+selectedBot;}
        }
        applyOfflineIdle();
        paintStatus();paintWings();paintMic();
        if(chatPanel!=null){chatPanel.setRemoteState(nativeWorking,nativeStale);chatPanel.setStatus(voiceTitle,conversation.busy());}
        layoutBand();
    }
    private void layoutBand(){
        if(stageDocked)return;
        if(root==null || root.getHeight()<=0 || statusBlock==null || controls==null || bot==null)return;
        boolean home=chatPanel==null && !ambient && statusBlock.getVisibility()==View.VISIBLE;
        int rowH=controls.getHeight()>0?controls.getHeight():dp(HomeBand.ROW_HEIGHT_DP);
        int statusH=statusBlock.getHeight()>0?statusBlock.getHeight():statusBlock.blockHeight();
        float density=getResources().getDisplayMetrics().density;
        HomeBand.Placement place=HomeBand.place(root.getHeight(),rowH,0,density);
        FrameLayout.LayoutParams sp=(FrameLayout.LayoutParams)statusBlock.getLayoutParams();
        FrameLayout.LayoutParams cp=(FrameLayout.LayoutParams)controls.getLayoutParams();
        if(sp.bottomMargin!=place.statusBottom || cp.bottomMargin!=place.rowBottom || cp.height!=place.rowHeight){
            sp.gravity=Gravity.BOTTOM;sp.width=-1;sp.height=-2;sp.bottomMargin=place.statusBottom;statusBlock.setLayoutParams(sp);
            cp.gravity=Gravity.BOTTOM;cp.width=-1;cp.height=place.rowHeight;cp.bottomMargin=place.rowBottom;controls.setLayoutParams(cp);
        }
        float ceiling=0;
        if(home)ceiling=statusBlock.getTop()>0?statusBlock.getTop():root.getHeight()-place.statusBottom-statusH;
        bot.setStatusShift(home?1f:0f,ceiling);
    }
    private void resetAmbientTimer(){handler.removeCallbacks(ambientTimer);if(resumed && !ambient && prefs.getBoolean("ambient_enabled",true))handler.postDelayed(ambientTimer,90000);}
    private void enterAmbient(){enterAmbient(false);}
    private void enterAmbient(boolean force){
        if(hands!=null && hands.cardOpen()){resetAmbientTimer();return;}
        if(!resumed || !unlocked() || !force && !prefs.getBoolean("ambient_enabled",true))return;
        if(overlay!=null || switchingBot || conversation.busy() || nativeWorking || conversation.listening() || conversation.micHeld() || voiceTitle.endsWith("is speaking") || chatImeBottom>0){resetAmbientTimer();return;}
        ambientOverview=bot.isOverview();ambient=true;connectionMonitor.setAmbient(true);
        cancelVoiceHandoff();hideAmbientControls();handler.postDelayed(ambientListen,700);
    }
    private void hideAmbientControls(){
        if(chatPanel!=null)chatPanel.hideKeyboard();
        for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child!=wall)child.setVisibility(View.INVISIBLE);}
        wall.setVisibility(View.VISIBLE);wall.bringToFront();syncAmbientWall();
        bot.setChatLayout(false);FrameLayout.LayoutParams bp=(FrameLayout.LayoutParams)bot.getLayoutParams();bp.width=-1;bp.height=-1;bot.setLayoutParams(bp);bot.setStatusShift(0,0);
    }
    private void exitAmbient(boolean listen){
        if(!ambient)return;ambient=false;connectionMonitor.setAmbient(false);ListenPreference.setMuted(prefs,false);handler.removeCallbacks(ambientListen);conversation.stopAudio();syncWakeHint();
        for(int i=0;i<root.getChildCount();i++){
            View child=root.getChildAt(i);
            if(child instanceof QuestionCard){
                child.setVisibility(hands!=null && hands.cardOpen()?View.VISIBLE:View.GONE);
                continue;
            }
            child.setVisibility(View.VISIBLE);
        }
        if(wall!=null)wall.setVisibility(View.GONE);
        if(bot.isOverview()!=ambientOverview)bot.setOverview(ambientOverview);
        if(chatPanel!=null){controls.setCenterVisible(false);homeStatus.setVisibility(View.INVISIBLE);bot.setChatLayout(true);layoutChat();}
        renderTask();resetAmbientTimer();if(listen && !prefs.getBoolean("muted",false)){requestVoiceHandoff(BotIdentity.PRIMARY);queueVoiceHandoff();}
    }
    private void listenAmbient(){
        if(!ambient || !resumed || !unlocked() || !ListenPreference.wakeEnabled(prefs) || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return;
        if(!switchingBot && !conversation.busy())armWake();
        handler.removeCallbacks(ambientListen);handler.postDelayed(ambientListen,5000);
    }
    private void refreshActivity(){
        if(bot==null || activityState==null)return;
        for(String name:BotIdentity.NAMES){
            boolean working=activityState.working(name) || name.equals(selectedBot) && conversation.busy();
            bot.setActivity(name,working,activityState.unread(name));
            TextView badge=chooserBadges.get(name);
            if(badge!=null){
                int hand=hands==null?0:hands.stage().handBadge(name);
                if(hand!=0){badge.setText("\u270B");badge.setTextColor(hand==2?0x73FFFFFF:Color.WHITE);}
                else{badge.setText(activityState.unread(name)?"●":working?"···":"");badge.setTextColor(activityState.unread(name)?0xffb9e5ff:0xffa7adb6);}
            }
        }
        if(botChooser!=null)botChooser.setText(selectedBot+" ▾"+(activityState.anyUnread()?"  ●":""));
        if(ambient)syncAmbientWall();
    }
    private void syncAmbientWall(){
        String newestWorking=null;long best=-1;Set<String> working=new HashSet<>(),unread=new HashSet<>();
        String firstUnread=null;
        for(String name:BotIdentity.NAMES){
            boolean busy=activityState.working(name) || name.equals(selectedBot) && conversation!=null && conversation.busy();
            if(busy){working.add(name);long started=taskStarted.getOrDefault(name,0L);if(newestWorking==null || started>=best){newestWorking=name;best=started;}}
            if(activityState.unread(name)){unread.add(name);if(firstUnread==null)firstUnread=name;}
        }
        String featured=featuredAmbientBot(oldestNeedsYou(),newestWorking,firstUnread);
        Map<String,String> lines=new HashMap<>();
        for(String name:BotIdentity.NAMES){
            boolean featuredWorking=name.equals(featured) && working.contains(name);
            String caption=AmbientCaptions.line(featuredWorking,taskDetails.get(name),unread.contains(name),activityState.preview(name));
            if(!caption.isEmpty())lines.put(name,caption);
        }
        wall.update(lines,working,unread);
    }
    /** Oldest needs you, then the newest working bot, then the first unread. */
    static String featuredAmbientBot(String oldestNeedsYou, String newestWorking, String firstUnread){
        if(oldestNeedsYou!=null && !oldestNeedsYou.isEmpty())return oldestNeedsYou;
        if(newestWorking!=null && !newestWorking.isEmpty())return newestWorking;
        return firstUnread==null?"":firstUnread;
    }
    private String oldestNeedsYou(){
        BotStageState stage=raiseHandStage();
        if(stage==null)return null;
        List<NeedsYouTiles.Tile> tiles=NeedsYouTiles.order(stage.items(),BotIdentity.NAMES,false);
        return tiles.isEmpty()?null:tiles.get(0).bot;
    }
    private void openAmbientBot(String name){
        if(!ambient)return;exitAmbient(false);if(!name.equals(selectedBot))selectBot(name,BotIdentity.color(name));
        showKeyboard(false,false);
    }
    private void saveChatPlace(){
        if(chatPanel!=null)prefs.edit().putString("chat_place_"+chatPanel.botName(),chatPanel.saveViewport().toString()).apply();
    }
    private String colorKey(){return selectedBot.isEmpty()?"ball_color":"bot_color_"+selectedBot;}
    private String historyKey(){return selectedBot.isEmpty()?"history":"bot_history_"+selectedBot;}
    private void showBotChooser(){
        if(switchingBot)return;
        newOverlay();
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12),dp(12),dp(12),dp(12));panel.setBackground(surface(0xff191c20,20));
        panel.setElevation(dp(12));panel.setOnClickListener(v->{});
        int top=dp(84),height=Math.min(dp(676),root.getHeight()-top-dp(24));
        FrameLayout.LayoutParams placement=new FrameLayout.LayoutParams(Math.min(dp(304),root.getWidth()-dp(48)),height,Gravity.TOP|Gravity.LEFT);
        placement.setMargins(dp(24),top,dp(24),dp(24));overlay.addView(panel,placement);
        overlayLayout=()->{placement.width=Math.min(dp(304),root.getWidth()-dp(48));placement.height=Math.min(dp(676),root.getHeight()-top-dp(24));panel.setLayoutParams(placement);};
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Bots",20);title.setPadding(dp(12),0,0,0);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(button("Close",this::closeOverlay));panel.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,dp(8),0,dp(8));scroll.addView(list);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(String name:BotIdentity.NAMES){
            boolean selected=name.equals(selectedBot);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(8),dp(14),dp(8));row.setMinimumHeight(dp(56));
            android.graphics.drawable.GradientDrawable background=surface(selected?0xff2b3036:0xff191c20,14,true);
            if(!selected)background.setStroke(0,Color.TRANSPARENT);
            row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x30ffffff),background,null));
            row.setFocusable(true);row.setSelected(selected);row.setContentDescription(name+(selected?", selected":""));
            ImageView avatar=new ImageView(this);avatar.setImageDrawable(SolidAvatar.icon(BotIdentity.shape(name),prefs.getInt("bot_color_"+name,BotIdentity.color(name))));
            row.addView(avatar,new LinearLayout.LayoutParams(dp(36),dp(36)));
            TextView label=text(name,17);label.setPadding(dp(14),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            TextView badge=text("",16);badge.setGravity(Gravity.CENTER);row.addView(badge,new LinearLayout.LayoutParams(dp(28),dp(32)));chooserBadges.put(name,badge);
            if(selected){TextView check=text("✓",19);check.setGravity(Gravity.CENTER);row.addView(check,new LinearLayout.LayoutParams(dp(28),dp(32)));}
            for(int i=0;i<row.getChildCount();i++)row.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.setOnClickListener(v->{closeOverlay();bot.touch();if(!name.equals(selectedBot))selectBot(name,BotIdentity.color(name));});
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(4);list.addView(row,rp);
        }
        refreshActivity();
        View divider=new View(this);divider.setBackgroundColor(0xff34383e);panel.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        Button checkReply=button("Check reply",()->{closeOverlay();conversation.checkReply();});
        LinearLayout.LayoutParams footer=new LinearLayout.LayoutParams(-1,dp(48));footer.topMargin=dp(8);panel.addView(checkReply,footer);
        if(android.animation.ValueAnimator.areAnimatorsEnabled()){
            panel.setAlpha(0);panel.setTranslationY(-dp(8));panel.animate().alpha(1).translationY(0).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }
    private void selectBot(String name,int color){selectBot(name,color,0);}
    private void selectBot(String name,int color,int direction){
        if(!pendingTaskBot.isEmpty() && !pendingTaskBot.equals(name)){pendingTask="";pendingTaskBot="";}
        if(!pendingVoiceBot.isEmpty() && !pendingVoiceBot.equals(name))cancelVoiceHandoff();
        if(switchingBot){bot.animate().withEndAction(null);bot.animate().cancel();switchingBot=false;bot.setTranslationX(0);bot.setTranslationY(0);bot.setScaleX(1);bot.setScaleY(1);}
        selectedBot=name;prefs.edit().putString("selected_bot",name).apply();
        nativeSnapshot=null;nativeWorking=false;nativeStale=true;nativeFeed.stop();if(resumed)nativeFeed.start(voiceServer(),name);
        conversation.select(voiceServer(),name);botChooser.setText(name.isEmpty()?"Bots ▾":name+" ▾");
        voiceTitle="Ready when you are";voiceDetail=name.isEmpty()?"Tap microphone to speak":"Talk to "+name;
        if(chatPanel!=null)showKeyboard(false);updateStatus();
        Runnable enter=()->{
            bot.setBotName(name);
            bot.setBallColor(prefs.getInt(colorKey(),color));bot.setSpeaking(false);
            bot.setTranslationX((direction==0?-1:direction)*root.getWidth()*.45f);bot.setTranslationY(-root.getHeight()*.3f);bot.setScaleX(.18f);bot.setScaleY(.18f);
            bot.animate().translationX(0).translationY(0).scaleX(1).scaleY(1).setDuration(650).setInterpolator(new android.view.animation.OvershootInterpolator(.45f)).withEndAction(()->{switchingBot=false;botSwitchReady(name);}).start();
        };
        if(bot.isOverview() || !android.animation.ValueAnimator.areAnimatorsEnabled()){bot.setBotName(name);bot.setBallColor(prefs.getInt(colorKey(),color));botSwitchReady(name);return;}
        switchingBot=true;bot.animate().translationX((direction==0?1:-direction)*root.getWidth()).setDuration(240).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(enter).start();
    }
    private void applyDemoIntent(Intent intent){
        if(demo==null || intent==null || !intent.hasExtra(DemoMode.EXTRA))return;
        boolean on=intent.getBooleanExtra(DemoMode.EXTRA,false);
        handler.postDelayed(()->{if(!destroyed)setDemo(on);},1200);
    }
    /** Demo mode shows the whole team on the ambient wall; tap it to see the same states on the carousel. */
    private void setDemo(boolean on){
        if(demo==null)return;
        if(on){demo.start();if(!ambient)enterAmbient(true);}
        else{demo.stop();exitAmbient(false);}
    }
    private void closeOverlay(){
        boolean resumeSetup=returnToSetup && computerSheet!=null;
        returnToSetup=false;
        if(overlay!=null){removeOverlay();if(resumeSetup)showSetup();return;}
        if(chatPanel!=null && android.animation.ValueAnimator.areAnimatorsEnabled()){
            if(closingChat)return;
            closingChat=true;chatPanel.hideKeyboard();
            FrameLayout.LayoutParams placement=(FrameLayout.LayoutParams)chatPanel.getLayoutParams();
            chatPanel.animate().cancel();
            chatPanel.animate().translationX(placement.width+placement.rightMargin).setDuration(240)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(this::removeChat).start();
            return;
        }
        removeChat();
    }
    private void removeChat(){
        saveChatPlace();prefs.edit().putBoolean("chat_open",false).apply();closingChat=false;
        if(chatPanel!=null){chatPanel.animate().cancel();chatPanel.animate().withEndAction(null);}
        if(chatPanel!=null){getWindow().setSoftInputMode(chatSoftInputMode);chatPanel.hideKeyboard();chatPanel=null;conversation.setTextChat(false);bot.setChatLayout(false);FrameLayout.LayoutParams bp=(FrameLayout.LayoutParams)bot.getLayoutParams();bp.width=-1;bp.height=-1;bot.setLayoutParams(bp);homeStatus.setVisibility(View.VISIBLE);controls.setCenterVisible(true);placeConnectionChip();layoutBand();armWake();}
        if(chatLayer!=null){root.removeView(chatLayer);chatLayer=null;}
        if(setupBehindChat){setupBehindChat=false;if(setup!=null && setup.showOnLaunch())showSetup();}
    }
    private void removeOverlay(){
        if(stageDocked){stageDocked=false;if(chatPanel==null){StageSheet.restore(root,bot,homeStatus,homeControls);layoutBand();}}
        computerSheet=null;setupPanel=null;chooserBadges.clear();overlayLayout=null;
        if(overlay!=null){root.removeView(overlay);overlay=null;drawer=null;for(int i=0;i<root.getChildCount();i++)root.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}
        showMicSettings(micPromptDenied);
    }
    private void newOverlay(){
        removeOverlay();for(int i=0;i<root.getChildCount();i++)root.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        overlay=new FrameLayout(this);overlay.setElevation(dp(24));overlay.setBackgroundColor(0x99000000);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));overlay.setOnClickListener(v->closeOverlay());
    }
    private void showDrawer(){
        newOverlay();panelWidth=Math.min(dp(400),root.getWidth()-dp(48));
        drawer=new LinearLayout(this);drawer.setOrientation(LinearLayout.VERTICAL);drawer.setPadding(dp(24),dp(16),dp(24),dp(24));drawer.setBackground(surface(0xff191c20,20));drawer.setOnClickListener(v->{});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(drawer);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(panelWidth,-1,Gravity.RIGHT);p.setMargins(0,dp(16),dp(16),dp(16));overlay.addView(scroll,p);
        overlayLayout=()->{panelWidth=Math.min(dp(400),root.getWidth()-dp(48));p.width=panelWidth;scroll.setLayoutParams(p);};
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("Settings",24);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(button("Close",this::closeOverlay));drawer.addView(header);
        addColorPicker();addStageControls();
        Switch ambientSwitch=new Switch(this);ambientSwitch.setText("Ambient mode · after 90 seconds");ambientSwitch.setTextColor(Color.WHITE);ambientSwitch.setMinHeight(dp(60));ambientSwitch.setChecked(prefs.getBoolean("ambient_enabled",true));ambientSwitch.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("ambient_enabled",on).apply();if(!on){exitAmbient(false);armWake();}resetAmbientTimer();});drawer.addView(ambientSwitch);
        drawer.addView(button("Enter ambient mode now",()->{closeOverlay();enterAmbient(true);}),new LinearLayout.LayoutParams(-1,dp(52)));
        TextView ambientNote=text("Shows the whole team. Max has the big tile, and the line under him says who is working or has a new reply. Tap a bot to open it.",13);ambientNote.setTextColor(0xffa7adb6);drawer.addView(ambientNote);
        LinearLayout.LayoutParams demoParams=new LinearLayout.LayoutParams(-1,dp(52));demoParams.topMargin=dp(8);
        drawer.addView(button(demo!=null && demo.on()?"Stop demo mode":"Demo mode",()->{boolean next=demo==null || !demo.on();closeOverlay();setDemo(next);}),demoParams);
        TextView demoNote=text("Shows the whole team busy: some working, some celebrating, some with a hand up, some doing nothing, and some with a new reply dot. Nothing is sent to any bot.",13);demoNote.setTextColor(0xffa7adb6);drawer.addView(demoNote);
        TextView section=text(selectedBot+" · Voice",14);section.setPadding(0,dp(24),0,dp(12));drawer.addView(section);
        LinearLayout.LayoutParams pickerParams=new LinearLayout.LayoutParams(-1,dp(52));
        drawer.addView(button("Choose voice",this::showVoicePicker),pickerParams);
        drawer.addView(new ListenSwitchRow(this,ListenPreference.switchOn(prefs),on->{
            ListenPreference.writeSwitch(prefs,on);cancelVoiceHandoff();
            if(on)armWake();else if(conversation.wakeArmed())conversation.stopWake();
            updateStatus();
        }));
        TextView note=text(VoiceCopy.DRAWER_NOTE,13);note.setTextColor(0xffa7adb6);drawer.addView(note);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(52));vp.topMargin=dp(8);drawer.addView(button("Computer connection",this::showVoiceConnection),vp);
        if(setup!=null && setup.showFinishRow()){LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52));fp.topMargin=dp(8);drawer.addView(button("Finish setup",()->{setup=setup.reopen();saveSetup();showSetup();}),fp);}
        LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(-1,dp(52));up.topMargin=dp(8);drawer.addView(button("Update app",this::showAppUpdate),up);
        TextView tablet=text("Tablet controls",14);tablet.setPadding(0,dp(20),0,dp(12));drawer.addView(tablet);
        drawerRow("Wi-Fi",Settings.ACTION_WIFI_SETTINGS);drawerRow("Bluetooth",Settings.ACTION_BLUETOOTH_SETTINGS);drawerRow("Display",Settings.ACTION_DISPLAY_SETTINGS);drawerRow("Sound",Settings.ACTION_SOUND_SETTINGS);drawerRow("All Android settings",Settings.ACTION_SETTINGS);
        Button home=button("Choose default Home",()->openSettings(Settings.ACTION_HOME_SETTINGS));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(52));hp.topMargin=dp(8);drawer.addView(home,hp);
        scroll.setTranslationX(panelWidth);scroll.animate().translationX(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    private void addStageControls(){
        TextView title=text("Stage",14);title.setPadding(0,dp(20),0,dp(8));drawer.addView(title);
        stageSlider("Bot size","stage_size",70,125);
        stageSlider("Spotlight brightness","stage_light",0,150);
        stageSlider("Animation intensity","stage_motion",0,150);
        drawer.addView(button("Reset stage",()->{prefs.edit().putInt("stage_size",100).putInt("stage_light",100).putInt("stage_motion",100).apply();bot.applyStagePreferences();showDrawer();}),new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private void stageSlider(String label,String key,int min,int max){
        int initial=prefs.getInt(key,100);
        TextView caption=text(label+" · "+initial+"%",14);caption.setPadding(0,dp(8),0,0);drawer.addView(caption);
        SeekBar slider=new SeekBar(this);slider.setContentDescription(label);slider.setMax(max-min);slider.setProgress(Math.max(0,Math.min(max-min,initial-min)));
        slider.setStateDescription(SliderState.description(initial));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar view,int progress,boolean user){int value=min+progress;caption.setText(label+" · "+value+"%");view.setStateDescription(SliderState.description(value));prefs.edit().putInt(key,value).apply();bot.applyStagePreferences();}
            public void onStartTrackingTouch(SeekBar view){}public void onStopTrackingTouch(SeekBar view){}
        });drawer.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private void addColorPicker(){
        TextView label=text("Color",14);label.setPadding(0,dp(20),0,dp(8));drawer.addView(label);
        ArrayList<ColorSwatch> swatches=new ArrayList<>();
        int selected=BallColors.normalize(prefs.getInt(colorKey(),BallColors.WHITE));
        for(int row=0;row<2;row++){
            LinearLayout choices=new LinearLayout(this);choices.setGravity(Gravity.CENTER);
            int start=row==0?0:6,end=row==0?6:BallColors.VALUES.length;
            for(int i=start;i<end;i++){
                final int color=BallColors.VALUES[i];ColorSwatch swatch=new ColorSwatch(this,i);swatch.setChecked(color==selected);swatches.add(swatch);
                swatch.setOnClickListener(v->{prefs.edit().putInt(colorKey(),color).apply();bot.setBallColor(color);for(ColorSwatch item:swatches)item.setChecked(item==swatch);});
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(48),dp(48));p.setMargins(dp(2),0,dp(2),0);choices.addView(swatch,p);
            }
            drawer.addView(choices,new LinearLayout.LayoutParams(-1,dp(48)));
        }
    }
    private void drawerRow(String label,String action){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(8);drawer.addView(button(label,()->openSettings(action)),p);}
    private void openSettings(String action){try{startActivity(new Intent(action));}catch(ActivityNotFoundException e){Toast.makeText(this,"This setting is unavailable",Toast.LENGTH_SHORT).show();}}
    private void showApps(){
        newOverlay();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(16),dp(24),dp(24));box.setBackground(surface(0xff191c20,20));box.setOnClickListener(v->{});
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.min(dp(680),root.getWidth()-dp(48)),-1,Gravity.CENTER);p.setMargins(0,dp(24),0,dp(24));overlay.addView(box,p);
        overlayLayout=()->{p.width=Math.min(dp(680),root.getWidth()-dp(48));box.setLayoutParams(p);};
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(text("Apps",24),new LinearLayout.LayoutParams(0,-2,1));head.addView(button("Close",this::closeOverlay));box.addView(head);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Search apps");search.setTextColor(Color.WHITE);search.setHintTextColor(0xffa7adb6);box.addView(search,new LinearLayout.LayoutParams(-1,dp(56)));
        ListView list=new ListView(this);box.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        PackageManager pm=getPackageManager();List<ResolveInfo> all=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        all.removeIf(info->info.activityInfo.packageName.equals(getPackageName()));all.sort(new ResolveInfo.DisplayNameComparator(pm));
        ArrayList<ResolveInfo> filtered=new ArrayList<>(all);
        BaseAdapter adapter=new BaseAdapter(){public int getCount(){return filtered.size();}public Object getItem(int i){return filtered.get(i);}public long getItemId(int i){return i;}public View getView(int i,View old,android.view.ViewGroup parent){
            LinearLayout row=new LinearLayout(HomeActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(8),dp(8),dp(8));row.setMinimumHeight(dp(60));ResolveInfo info=filtered.get(i);
            ImageView icon=new ImageView(HomeActivity.this);icon.setImageDrawable(info.loadIcon(pm));row.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));TextView label=text(info.loadLabel(pm).toString(),17);label.setPadding(dp(16),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));return row;
        }};list.setAdapter(adapter);
        TextView empty=text("No matching apps",16);empty.setGravity(Gravity.CENTER);box.addView(empty);list.setEmptyView(empty);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filtered.clear();for(ResolveInfo info:all)if(info.loadLabel(pm).toString().toLowerCase(Locale.ROOT).contains(s.toString().toLowerCase(Locale.ROOT)))filtered.add(info);adapter.notifyDataSetChanged();}public void afterTextChanged(android.text.Editable e){}});
        list.setOnItemClickListener((a,v,i,id)->{ResolveInfo info=filtered.get(i);try{startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(info.activityInfo.packageName,info.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));closeOverlay();}catch(RuntimeException e){Toast.makeText(this,"Unable to open this app",Toast.LENGTH_SHORT).show();}});
    }
    private void showVoicePicker(){
        if(!conversation.speechReady()){Toast.makeText(this,"Tablet speech is starting. Try again shortly.",Toast.LENGTH_LONG).show();return;}
        List<android.speech.tts.Voice> voices=conversation.voices();
        if(voices.isEmpty()){
            new AlertDialog.Builder(this).setTitle("No installed voices").setMessage("Install a voice in Android text-to-speech settings, then return here.").setPositiveButton("Speech settings",(d,w)->openSettings("com.android.settings.TTS_SETTINGS")).setNegativeButton("Cancel",null).show();return;
        }
        String[] labels=new String[voices.size()+1];labels[0]="Tablet default";int checked=0;
        for(int i=0;i<voices.size();i++){
            android.speech.tts.Voice v=voices.get(i);
            labels[i+1]=v.getLocale().getDisplayName()+" · "+v.getName();
            if(v.getName().equals(conversation.savedVoice()))checked=i+1;
        }
        final int[] choice={checked};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(selectedBot+" · Voice")
            .setSingleChoiceItems(labels,checked,(d,which)->choice[0]=which)
            .setPositiveButton("Save",(d,w)->conversation.saveVoice(choice[0]==0?"":voices.get(choice[0]-1).getName()))
            .setNegativeButton("Cancel",null).setNeutralButton("Preview",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
            if(!conversation.previewVoice(choice[0]==0?"":voices.get(choice[0]-1).getName()))Toast.makeText(this,"This voice is unavailable",Toast.LENGTH_SHORT).show();
        }));
        dialog.setOnDismissListener(d->conversation.finishVoicePreview());dialog.show();
    }
    private void saveSetup(){
        prefs.edit().putBoolean("setup_started",true).putInt("setup_done",setup.done).putInt("setup_skipped",setup.skipped).putBoolean("setup_dismissed",setup.dismissed).putBoolean("setup_home_attempted",setup.homeAttempted).apply();
    }
    private void refreshSetupGates(){
        if(setup==null || setup.existingInstall)return;
        boolean mic=checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        if(mic){micPromptDenied=false;showMicSettings(false);}
        SetupProgress next=setup.reconcile(mic,homeRoleHeld());
        if(next!=setup){setup=next;saveSetup();}
        if(setupPanel!=null)bindSetup();
    }
    private boolean homeRoleHeld(){
        RoleManager roles=getSystemService(RoleManager.class);
        return roles!=null && roles.isRoleHeld(RoleManager.ROLE_HOME);
    }
    private void showMicSettings(boolean show){
        if(micSettings==null || homeStatus==null)return;
        micSettings.setVisibility(show && setupPanel==null?View.VISIBLE:View.GONE);
    }
    private void openAppSettings(){
        try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.fromParts("package",getPackageName(),null)));}
        catch(ActivityNotFoundException e){openSettings(Settings.ACTION_SETTINGS);}
    }
    private void showSetup(){
        if(setup==null)return;
        setup=setup.reopen();saveSetup();
        newOverlay();
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setOnClickListener(v->{});
        setupPanel=new SetupPanel(this,new SetupPanel.Host(){
            public void meet(){meetMax();}
            public void connect(){returnToSetup=true;showVoiceConnection();}
            public void allowMic(){requestMicrophone();}
            public void openMicSettings(){openAppSettings();}
            public void useKeyboard(){useKeyboardInstead();}
            public void chooseHome(){requestHomeRole();}
            public void skipCurrent(){skipSetupStep();}
            public void finish(){finishSetup();}
        });
        overlay.addView(setupPanel,new FrameLayout.LayoutParams(dp(460),-1));
        overlayLayout=this::dockStageSheet;
        bindSetup();
        if(!setup.stepDone(SetupProgress.MEET) && bot!=null)bot.play(BotTrick.Name.BOUNCE);
    }
    private void bindSetup(){
        if(setupPanel!=null){
            boolean settingsInstead=micPromptDenied && !shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO);
            setupPanel.bind(setup,connection,micPromptDenied,settingsInstead);
            dockStageSheet();
        }
        updateStatus();
    }
    private void meetMax(){
        if(bot!=null)bot.play(BotTrick.Name.BOUNCE);
        setup=setup.focus(SetupProgress.MEET).complete(SetupProgress.MEET);saveSetup();bindSetup();
    }
    private void requestMicrophone(){
        setup=setup.focus(SetupProgress.MIC);saveSetup();
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){setup=setup.complete(SetupProgress.MIC);saveSetup();bindSetup();return;}
        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},7);
    }
    private void useKeyboardInstead(){
        setup=setup.skip(SetupProgress.MIC);saveSetup();setupBehindChat=true;returnToSetup=false;removeOverlay();showKeyboard(false);
    }
    private void requestHomeRole(){
        setup=setup.focus(SetupProgress.HOME).attemptHome();saveSetup();
        RoleManager roles=getSystemService(RoleManager.class);
        if(roles!=null && roles.isRoleAvailable(RoleManager.ROLE_HOME)){startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_HOME));return;}
        openSettings(Settings.ACTION_HOME_SETTINGS);
    }
    private void skipSetupStep(){
        int step=setup.current();if(step<0)return;
        setup=setup.skip(step);saveSetup();bindSetup();
    }
    private void finishSetup(){
        setup=setup.dismiss();saveSetup();returnToSetup=false;removeOverlay();
        voiceTitle="Ready when you are";voiceDetail="Say Hey Bots";updateStatus();
        if(bot!=null && !bot.isOverview())bot.setOverview(true);
        if(!prefs.getBoolean("pinch_hint_shown",false)){prefs.edit().putBoolean("pinch_hint_shown",true).apply();showPinchHint();}
    }
    private void showPinchHint(){
        if(pinchHint!=null || root==null)return;
        pinchHint=text("Pinch to see the whole team",15);pinchHint.setTextColor(0xffa7adb6);pinchHint.setGravity(Gravity.CENTER);
        pinchHint.setIncludeFontPadding(false);
        pinchHint.setBackground(surface(0xff121416,18));pinchHint.setPadding(dp(16),dp(4),dp(16),dp(4));
        pinchHint.setOnClickListener(v->hidePinchHint());
        int top=dp(420);
        boolean overview=bot instanceof BotView && ((BotView)bot).isOverview();
        if(overview){
            float density=getResources().getDisplayMetrics().density;
            top=Math.round(BotView.overviewNameBottom(root.getWidth(),root.getHeight(),density,((BotView)bot).stageScale()))+dp(16);
        }else if(bot instanceof BotView) top=Math.round(((BotView)bot).restingBodyBottom())+dp(16);
        pinchHint.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        if(homeStatus!=null){
            FrameLayout.LayoutParams statusParams=(FrameLayout.LayoutParams)homeStatus.getLayoutParams();
            int statusH=homeStatus.getHeight()>0?homeStatus.getHeight():dp(72);
            int statusTop=root.getHeight()-statusParams.bottomMargin-statusH;
            if(overview){
                int need=top+pinchHint.getMeasuredHeight()+dp(8);
                if(statusTop<need){
                    if(pinchStatusMargin<0)pinchStatusMargin=statusParams.bottomMargin;
                    statusParams.bottomMargin=Math.max(dp(16),statusParams.bottomMargin-(need-statusTop));
                    homeStatus.setLayoutParams(statusParams);
                }
            }else if(top+dp(48)>statusTop-dp(8)) top=statusTop+statusH+dp(8);
        }
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        lp.topMargin=Math.max(dp(88),top);
        root.addView(pinchHint,lp);handler.postDelayed(this::hidePinchHint,6000);
    }
    private void hidePinchHint(){
        if(pinchHint!=null){root.removeView(pinchHint);pinchHint=null;}
        if(pinchStatusMargin>=0 && homeStatus!=null){
            FrameLayout.LayoutParams statusParams=(FrameLayout.LayoutParams)homeStatus.getLayoutParams();
            statusParams.bottomMargin=pinchStatusMargin;homeStatus.setLayoutParams(statusParams);pinchStatusMargin=-1;
        }
    }
    private void showVoiceConnection(){
        newOverlay();
        overlay.setBackgroundColor(Color.TRANSPARENT);
        computerSheet=new ComputerSheet(this,new ComputerSheet.Host(){
            public void close(){closeOverlay();}
            public void tryNow(){connectionMonitor.tryNow();}
            public void save(String stored){applyServer(stored);}
            public String address(){return voiceServer();}
        });
        overlay.addView(computerSheet,new FrameLayout.LayoutParams(dp(460),-1));
        overlayLayout=this::dockStageSheet;
        dockStageSheet();
        computerSheet.bind(connection);
        if(android.animation.ValueAnimator.areAnimatorsEnabled() && root.getWidth()>=root.getHeight()){
            int width=computerSheet.getLayoutParams().width;
            computerSheet.setTranslationX(width);computerSheet.animate().translationX(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }
    private void dockStageSheet(){
        View sheet=computerSheet!=null?computerSheet:setupPanel;
        if(sheet==null)return;
        StageSheet.dock(root,bot,homeStatus,homeControls,sheet);
        stageDocked=true;
    }
    private void applyServer(String address){
        prefs.edit().putString("voice_server",address).apply();
        if(setup!=null && !setup.existingInstall){setup=setup.complete(SetupProgress.CONNECT);saveSetup();if(setupPanel!=null)bindSetup();}
        conversation.select(address,selectedBot);
        nativeSnapshot=null;nativeWorking=false;nativeStale=true;
        if(resumed){nativeFeed.start(address,selectedBot);overviewFeed.start(address);connectionMonitor.tryNow();}
        if(!selectedBot.isEmpty())conversation.resume();
    }
    private void applyConnection(ConnectionState snapshot){
        if(connectionChip!=null){connectionChip.bind(snapshot.mode());connectionChip.setVisibility(snapshot.known() && prefs.contains("voice_server")?View.VISIBLE:View.GONE);}
        if(snapshot.known() && snapshot.mode()!=ConnectionRules.Mode.OFFLINE && (ConnectionRules.OFFLINE_TITLE.equals(voiceTitle) || "Connection interrupted".equals(voiceTitle))){
            voiceTitle="Ready when you are";voiceDetail=selectedBot.isEmpty()?"Tap microphone to speak":"Say Hey Bots";
        }
        updateStatus();
        if(setup!=null && !setup.existingInstall && !setup.stepDone(SetupProgress.CONNECT) && prefs.contains("voice_server") && snapshot.voiceAnswering()){
            setup=setup.complete(SetupProgress.CONNECT);saveSetup();
        }
        if(computerSheet!=null)computerSheet.bind(snapshot);
        if(setupPanel!=null){boolean settingsInstead=micPromptDenied && !shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO);setupPanel.bind(setup,snapshot,micPromptDenied,settingsInstead);dockStageSheet();}
    }
    /** Idle status follows the chip. A finished stop must not leave "Ready when you are" over Offline. */
    private void applyOfflineIdle(){
        if(setupPanel!=null || taskProgress.phase()!=TaskProgress.Phase.IDLE)return;
        if(connection==null || !connection.known() || connection.mode()!=ConnectionRules.Mode.OFFLINE || !idleForOffline())return;
        String detail=ConnectionRules.OFFLINE_TITLE.equals(voiceTitle) && ConnectionRules.OFFLINE_ASLEEP.equals(voiceDetail)?ConnectionRules.OFFLINE_ASLEEP:ConnectionRules.OFFLINE_SAVED;
        voiceTitle=ConnectionRules.OFFLINE_TITLE;
        voiceDetail=detail;
    }
    private boolean idleForOffline(){
        if(conversation.listening() || conversation.micHeld() || conversation.busy() || nativeWorking)return false;
        if(voiceTitle.endsWith("is speaking") || "Listening".equals(voiceTitle) || "One moment".equals(voiceTitle))return false;
        return "Ready when you are".equals(voiceTitle) || ConnectionRules.OFFLINE_TITLE.equals(voiceTitle) || "Connection interrupted".equals(voiceTitle);
    }
    private void placeConnectionChip(){
        if(connectionChip==null)return;
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)connectionChip.getLayoutParams();
        int right=dp(24);
        if(chatPanel!=null && root.getWidth()>root.getHeight()){FrameLayout.LayoutParams chat=(FrameLayout.LayoutParams)chatPanel.getLayoutParams();right=chat.width+chat.rightMargin+dp(24);}
        if(hands!=null && hands.cardOpen() && root.getWidth()>root.getHeight()){
            int cardRight=QuestionCard.panelWidth(root.getWidth(),root.getHeight(),getResources().getDisplayMetrics().density)+dp(16)+dp(24);
            if(cardRight>right)right=cardRight;
        }
        lp.setMargins(dp(24),dp(24),right,0);connectionChip.setLayoutParams(lp);
        if(overlay==null)connectionChip.bringToFront();
    }
    private String draftKey(){return "bot_draft_"+selectedBot;}
    private void showAppUpdate(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(20),dp(24),dp(20));
        box.addView(text("Update app",22));TextView detail=text("Checking your computer…",15);detail.setPadding(0,dp(12),0,dp(16));box.addView(detail);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);box.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.RIGHT);controls.setPadding(0,dp(18),0,0);Button update=button("Download",()->{});update.setEnabled(false);controls.addView(update);Button close=button("Close",()->{});LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,dp(48));gap.leftMargin=dp(12);controls.addView(close,gap);box.addView(controls);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(box).create();close.setOnClickListener(v->dialog.dismiss());dialog.setOnShowListener(d->dialog.getWindow().setBackgroundDrawable(surface(0xff191c20,20)));dialog.show();
        String base=voiceServer();
        AppUpdater.Listener listener=new AppUpdater.Listener(){
            public void available(AppUpdateInfo info){if(!dialog.isShowing())return;progress.setVisibility(View.GONE);
                if(info.code<=appUpdater.installedVersion()){detail.setText("You’re up to date.");update.setVisibility(View.GONE);return;}
                detail.setText("Version "+info.name+" is ready. Android will ask you to approve installation.");update.setEnabled(true);
                update.setOnClickListener(v->{update.setEnabled(false);close.setEnabled(false);dialog.setCancelable(false);progress.setVisibility(View.VISIBLE);progress.setIndeterminate(false);detail.setText("Downloading update…");appUpdater.download(base,info,this);});
            }
            public void progress(int percent){if(dialog.isShowing()){progress.setProgress(percent);detail.setText("Downloading update… "+percent+"%");}}
            public void ready(java.io.File apk){dialog.dismiss();pendingInstall=apk;
                if(getPackageManager().canRequestPackageInstalls())openUpdateInstaller();
                else new AlertDialog.Builder(HomeActivity.this).setTitle("Allow app updates").setMessage("Allow Grok Home to install updates, then return here. Android will still ask before installing.").setPositiveButton("Open settings",(d,w)->{try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,android.net.Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException error){pendingInstall=null;Toast.makeText(HomeActivity.this,"Open Android’s Install unknown apps settings for Grok Home.",Toast.LENGTH_LONG).show();}}).setNegativeButton("Cancel",(d,w)->pendingInstall=null).show();
            }
            public void failed(String message){if(!dialog.isShowing())return;progress.setVisibility(View.GONE);detail.setText(message);close.setEnabled(true);dialog.setCancelable(true);update.setText("Try again");update.setEnabled(true);update.setOnClickListener(v->{dialog.dismiss();showAppUpdate();});}
        };
        appUpdater.check(base,listener);
    }
    private void openUpdateInstaller(){
        java.io.File file=pendingInstall;pendingInstall=null;if(file==null || !file.isFile())return;
        try{android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",file);
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("App update",uri));startActivity(intent);
        }catch(RuntimeException error){Toast.makeText(this,"Couldn’t open the Android installer. Try Update app again.",Toast.LENGTH_LONG).show();}
    }
    private void showKeyboard(){showKeyboard(true);}
    private void showKeyboard(boolean animate){showKeyboard(animate,animate || chatImeBottom>0);}
    private void showKeyboard(boolean animate,boolean keyboard){
        if(chatPanel!=null && animate){if(keyboard)chatPanel.focusComposer();return;}
        saveChatPlace();prefs.edit().putBoolean("chat_open",true).apply();
        if(chatPanel!=null){chatPanel.animate().withEndAction(null);chatPanel.animate().cancel();chatLayer.removeView(chatPanel);chatPanel=null;closingChat=false;}
        conversation.setTextChat(true);
        if(chatLayer==null){
            chatSoftInputMode=getWindow().getAttributes().softInputMode;
            chatLayer=new FrameLayout(this);chatLayer.setElevation(dp(20));root.addView(chatLayer,new FrameLayout.LayoutParams(-1,-1));
        }
        if(!prefs.contains(draftKey()) && prefs.contains("draft"))prefs.edit().putString(draftKey(),prefs.getString("draft","")).remove("draft").apply();
        chatPanel=new TextChatPanel(this,selectedBot,prefs.getInt(colorKey(),BotIdentity.color(selectedBot)),prefs.getString(draftKey(),""),new TextChatPanel.Actions(){
            public void close(){closeOverlay();}
            public void voice(){toggleMicrophone();}
            public void viewed(String token){activityState.viewed(selectedBot,token);refreshActivity();}
            public void draft(String value){prefs.edit().putString(draftKey(),value).apply();}
            public void send(String message){if(handleVoiceCommand(message))return;if(conversation.busy() || nativeWorking)return;conversation.sendText(message);if(chatPanel!=null)chatPanel.setStatus(voiceTitle,conversation.busy());}
        });
        try{chatPanel.restoreViewport(new org.json.JSONObject(prefs.getString("chat_place_"+selectedBot,"{}")));}catch(org.json.JSONException ignored){}
        FrameLayout.LayoutParams placement=new FrameLayout.LayoutParams(dp(460),-1,Gravity.TOP|Gravity.RIGHT);chatLayer.addView(chatPanel,placement);
        homeStatus.setVisibility(View.INVISIBLE);controls.setCenterVisible(false);bot.setChatLayout(true);layoutBand();layoutChat();conversation.stopWake();
        chatPanel.setHistory(prefs.getString(historyKey(),""));chatPanel.setStatus(voiceTitle,conversation.busy());
        chatPanel.setRemoteState(nativeWorking,nativeStale);if(nativeSnapshot!=null)chatPanel.setNativeHistory(nativeSnapshot.optJSONArray("entries"));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);root.requestApplyInsets();
        if(animate && android.animation.ValueAnimator.areAnimatorsEnabled()){
            FrameLayout.LayoutParams chatPlacement=(FrameLayout.LayoutParams)chatPanel.getLayoutParams();
            chatPanel.setTranslationX(chatPlacement.width+chatPlacement.rightMargin);
            chatPanel.animate().translationX(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        if(keyboard)chatPanel.focusComposer();
    }
    private void layoutChat(){
        if(chatPanel==null || ambient)return;
        boolean portrait=root.getHeight()>root.getWidth();
        int available=root.getHeight()-Math.max(dp(24),chatImeBottom+dp(12));
        int avatarHeight=portrait?Math.max(dp(190),Math.min(dp(360),Math.round(available*.36f))):available;
        int width=portrait?root.getWidth()-dp(48):Math.min(dp(460),Math.round(root.getWidth()*.40f));
        int top=portrait?avatarHeight:dp(24);
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)chatPanel.getLayoutParams();p.width=width;int height=Math.max(dp(190),available-top);if(height!=p.height)chatPanel.prepareResize();p.height=height;p.setMargins(0,top,dp(24),0);chatPanel.setLayoutParams(p);
        FrameLayout.LayoutParams bp=(FrameLayout.LayoutParams)bot.getLayoutParams();bp.width=portrait?root.getWidth():Math.max(dp(200),root.getWidth()-width-dp(48));bp.height=portrait?avatarHeight:Math.max(dp(190),root.getHeight()-chatImeBottom);bp.gravity=Gravity.TOP|Gravity.LEFT;bot.setLayoutParams(bp);
        placeConnectionChip();
    }
    private void saveTranscript(String role,String value){
        String line=role+": "+value+"\n\n";
        String history=prefs.getString(historyKey(),"")+line;
        if(history.length()>24000)history=history.substring(history.length()-24000);
        prefs.edit().putString(historyKey(),history).apply();
        if(role.equals("You")){prefs.edit().remove("draft").remove(draftKey()).apply();if(chatPanel!=null)chatPanel.clearComposer();}
        if(chatPanel!=null)chatPanel.appendTranscript(role,value);
    }
    private void showHistory(){
        ScrollView scroll=new ScrollView(this);TextView content=text(prefs.getString(historyKey(),"").isEmpty()?"No conversations yet.":prefs.getString(historyKey(),""),16);
        content.setTextIsSelectable(true);content.setPadding(dp(24),dp(16),dp(24),dp(16));scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(selectedBot.isEmpty()?"History":selectedBot+" · History").setView(scroll).setPositiveButton("Done",null).setNeutralButton("Clear",(d,w)->prefs.edit().remove(historyKey()).apply()).show();
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
        if(ambient)return super.dispatchTouchEvent(event);
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)resetAmbientTimer();
        float x=event.getX(), y=event.getY();
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){downX=x;downY=y;touchWasMulti=false;}
        if(event.getPointerCount()>1)touchWasMulti=true;
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP && Math.abs(y-downY)<dp(90)){
            float delta=x-downX;
            if(drawer!=null && delta>dp(70)){closeOverlay();return true;}
            if(overlay==null && chatPanel!=null && downX>=chatPanel.getLeft() && downY>=chatPanel.getTop() && downX<=chatPanel.getRight() && downY<=chatPanel.getBottom() && !touchWasMulti && delta>dp(70) && Math.abs(delta)>Math.abs(y-downY)*1.5f){closeOverlay();return true;}
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public void onBackPressed(){if(overlay!=null)removeOverlay();else if(hands!=null && hands.cardOpen())hands.closeCard();else if(chatPanel!=null)chatPanel.hideKeyboard();else super.onBackPressed();}
    private void trackHands(org.json.JSONObject snapshot){
        if(hands==null || snapshot==null)return;
        String name=snapshot.optString("bot");
        hands.observe(snapshot,name.equals(selectedBot) && conversation.busy(),taskPhases.getOrDefault(name,""));
        hands.push(bot);
    }
    private void openRaisedHand(String name){
        if(ambient)exitAmbient(false);
        if(!raisedQuestion(name))return;
        hands.open(name);
    }
    /** Ember opens the read-only card from a wall tile without a second card implementation. */
    void showRaiseHandCard(String name){
        if(name==null || BotIdentity.index(name)<0)return;
        if(!raisedQuestion(name)){
            if(ambient)openAmbientBot(name);
            return;
        }
        if(ambient)exitAmbient(false);
        if(!name.equals(selectedBot)){if(bot.isOverview())bot.centerBot(name);else selectBot(name,BotIdentity.color(name));}
        openRaisedHand(name);
    }
    private boolean raisedQuestion(String name){
        return hands!=null && name!=null && NeedsYou.hasQuestion(hands.stage().hands(name));
    }
    /** Live reducer. It implements {@link NeedsYouSource}. Pass it to the wall. */
    BotStageState raiseHandStage(){return hands==null?null:hands.stage();}
    private boolean computerOffline(){return connection!=null && connection.known() && connection.mode()==ConnectionRules.Mode.OFFLINE;}
    private boolean voiceBusy(){
        if(conversation!=null && (conversation.listening() || conversation.micHeld()))return true;
        return voiceTitle!=null && (voiceTitle.endsWith("is speaking") || "Listening".equals(voiceTitle) || "One moment".equals(voiceTitle));
    }
    private boolean needsYouLine(){
        if(hands==null || selectedBot.isEmpty() || voiceBusy() || computerOffline())return false;
        return hands.stage().frame(selectedBot).state==BotStageState.State.NEEDS_YOU;
    }
    private boolean reducedMotion(){
        if(!android.animation.ValueAnimator.areAnimatorsEnabled())return true;
        if(prefs.getInt("stage_motion",100)<=0)return true;
        android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        if(power!=null && power.isPowerSaveMode())return true;
        if(power!=null && power.getCurrentThermalStatus()>=android.os.PowerManager.THERMAL_STATUS_MODERATE)return true;
        android.content.Intent battery=registerReceiver(null,new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null){
            int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,100);
            int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            int plugged=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,1);
            int pct=scale<=0?100:level*100/scale;
            if(plugged==0 && pct<=15)return true;
        }
        return false;
    }
    private boolean chatHiddenForCard;
    private void setRaiseCardOpen(boolean open){
        if(open){
            handler.removeCallbacks(ambientTimer);
            if(chatPanel!=null){chatHiddenForCard=true;chatPanel.setVisibility(View.INVISIBLE);}
            else{
                chatHiddenForCard=false;
                if(homeStatus!=null)homeStatus.setVisibility(View.INVISIBLE);
                if(controls!=null)controls.setCenterVisible(false);
                bot.setChatLayout(true);
            }
        }else{
            if(chatHiddenForCard && chatPanel!=null){chatPanel.setVisibility(View.VISIBLE);chatHiddenForCard=false;}
            else if(chatPanel==null){
                if(homeStatus!=null)homeStatus.setVisibility(View.VISIBLE);
                if(controls!=null)controls.setCenterVisible(true);
                bot.setChatLayout(false);
            }
            resetAmbientTimer();
        }
        placeConnectionChip();
        layoutBand();
    }
}

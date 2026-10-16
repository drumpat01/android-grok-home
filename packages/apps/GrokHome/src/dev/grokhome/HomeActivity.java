package dev.grokhome;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Rotating launcher with foreground-only Grok voice. */
public final class HomeActivity extends Activity {
    private FrameLayout root, overlay, chatLayer;
    private LinearLayout drawer;
    private BotView bot;
    private TextView state, subtitle;
    private SharedPreferences prefs;
    private BotConversation conversation;
    private AppUpdater appUpdater;
    private java.io.File pendingInstall;
    private NativeActivityFeed nativeFeed;
    private BotOverviewFeed overviewFeed;
    private BotActivityState activityState;
    private final Map<String,TextView> chooserBadges=new HashMap<>();
    private org.json.JSONObject nativeSnapshot;
    private boolean nativeWorking,nativeStale=true;
    private TextChatPanel chatPanel;
    private View homeStatus,homeControls;
    private int chatImeBottom,chatSoftInputMode;
    private Button botChooser,cancelTaskButton;
    private LinearLayout taskCard;
    private TextView taskLabel;
    private boolean ambient,ambientOverview,awaitingRoute,ambientTouch;
    private String pendingTaskBot="",pendingTask="";
    private boolean pendingRoute;
    private final Map<String,String> taskPhases=new HashMap<>(),taskDetails=new HashMap<>();
    private final Map<String,Boolean> observedWorking=new HashMap<>();
    private final Map<String,Long> taskStarted=new HashMap<>();
    private final Runnable ambientTimer=this::enterAmbient;
    private final Runnable ambientListen=this::listenAmbient;
    private final Runnable taskTick=new Runnable(){public void run(){if(!resumed)return;renderTask();handler.postDelayed(this,1000);}};
    private String selectedBot="";
    private boolean switchingBot;
    private String pendingVoiceBot="";
    private Runnable pendingVoiceStart;
    private ImageButton microphoneButton;
    private boolean resumed, destroyed, pendingListen;
    private String voiceTitle="Ready when you are", voiceDetail="Tap microphone to speak";
    private final BroadcastReceiver screenOff=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){exitAmbient(false);handler.removeCallbacks(ambientTimer);handler.removeCallbacks(ambientListen);cancelVoiceHandoff();if(conversation!=null)conversation.pause();if(nativeFeed!=null)nativeFeed.stop();if(overviewFeed!=null)overviewFeed.stop();if(activityState!=null){activityState.clearWorking();refreshActivity();}}
            else if(Intent.ACTION_USER_PRESENT.equals(i.getAction())){if(resumed && !selectedBot.isEmpty()){conversation.resume();nativeFeed.start(voiceServer(),selectedBot);overviewFeed.start(voiceServer());}resetAmbientTimer();maybeAutoListen();}
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
    private GradientDrawable surface(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), 0xff34383e); return d;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(15);
        b.setMinHeight(dp(48)); b.setPadding(dp(18), dp(4), dp(18), dp(4)); b.setBackground(surface(0xff17191c, 28));
        b.setOnClickListener(v -> action.run()); return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); prefs = getSharedPreferences("home", MODE_PRIVATE);
        activityState=new BotActivityState(prefs);
        appUpdater=new AppUpdater(this);
        SharedPreferences.Editor identities=prefs.edit();
        for(int i=0;i<BotIdentity.NAMES.length;i++){String key="bot_color_"+BotIdentity.NAMES[i];if(!prefs.contains(key))identities.putInt(key,BotIdentity.COLORS[i]);}
        identities.apply();
        // Apply the default identity once; subsequent color-picker changes still persist.
        if(!prefs.getBoolean("max_red_identity",false))prefs.edit().putInt("bot_color_Max",BallColors.RED).putBoolean("max_red_identity",true).apply();
        if(!prefs.getBoolean("kit_white_identity",false))prefs.edit().putInt("bot_color_Nova",BallColors.WHITE).putBoolean("kit_white_identity",true).apply();
        selectedBot=prefs.getString("selected_bot","Max");
        if(selectedBot.isEmpty()){selectedBot="Max";prefs.edit().putString("selected_bot",selectedBot).apply();}
        if(!selectedBot.isEmpty())voiceDetail="Talk to "+selectedBot;
        conversation=new BotConversation(this,new BotConversation.Listener(){
            public void state(String title,String detail){if(!destroyed && !selectedBot.isEmpty()){voiceTitle=title;voiceDetail=detail;updateStatus();if(ambient && !title.equals("Listening")){handler.removeCallbacks(ambientListen);handler.postDelayed(ambientListen,2500);}}}
            public void speaking(boolean value){if(!destroyed && bot!=null && !selectedBot.isEmpty())bot.setSpeaking(value);}
            public void transcript(String role,String value){if(!destroyed)saveTranscript(role,value);}
            public boolean command(String spoken){return handleVoiceCommand(spoken);}
            public void routed(String name,String request){switchWithTask(name,request,false);}
            public void task(String phase,String detail){taskPhases.put(selectedBot,phase);taskDetails.put(selectedBot,detail);if(phase.equals("working"))taskStarted.putIfAbsent(selectedBot,System.currentTimeMillis());renderTask();}
        });
        conversation.select(voiceServer(),selectedBot);
        nativeFeed=new NativeActivityFeed(snapshot->{
            if(destroyed || !resumed || !selectedBot.equals(snapshot.optString("bot")))return;
            observeTask(snapshot);activityState.update(snapshot);refreshActivity();
            nativeStale=snapshot.optBoolean("stale");nativeWorking=!nativeStale && snapshot.optBoolean("working");
            if(!nativeStale)nativeSnapshot=snapshot;
            updateStatus();if(chatPanel!=null && !nativeStale)chatPanel.setNativeHistory(snapshot.optJSONArray("entries"));
        });
        overviewFeed=new BotOverviewFeed(snapshot->{if(!destroyed && resumed){observeTask(snapshot);activityState.update(snapshot);refreshActivity();}});
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK); setContentView(root);
        bot = new BotView(this); root.addView(bot, new FrameLayout.LayoutParams(-1,-1));
        bot.setBotName(selectedBot);
        bot.setBallColor(prefs.getInt(colorKey(),BallColors.WHITE));
        bot.restoreOverview(prefs.getBoolean("stage_overview",false));
        botChooser=button(selectedBot.isEmpty()?"Bots ▾":selectedBot+" ▾",this::showBotChooser);
        botChooser.setContentDescription("Choose bot");
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.LEFT);bp.setMargins(dp(24),dp(24),dp(24),0);root.addView(botChooser,bp);
        LinearLayout status = new LinearLayout(this); status.setOrientation(LinearLayout.VERTICAL); status.setGravity(Gravity.CENTER);
        state = text("Ready when you are", 24); subtitle = text("Tap microphone to speak", 13); subtitle.setTextColor(0xffa7adb6);
        state.setGravity(Gravity.CENTER);subtitle.setGravity(Gravity.CENTER);
        status.addView(state); status.addView(subtitle);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM); sp.bottomMargin=dp(108); root.addView(status,sp);homeStatus=status;
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        addControl(controls, "Keyboard", this::showKeyboard);
        addControl(controls, "Microphone", this::toggleMicrophone);
        addControl(controls, "History", this::showHistory);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1,dp(56),Gravity.BOTTOM); cp.bottomMargin=dp(42); root.addView(controls,cp);homeControls=controls;
        Button apps=button("Apps",this::showApps); FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(dp(106),dp(48),Gravity.BOTTOM|Gravity.LEFT); ap.setMargins(dp(24),0,0,dp(18));root.addView(apps,ap);
        Button settings=button("Settings",this::showDrawer);FrameLayout.LayoutParams st=new FrameLayout.LayoutParams(dp(116),dp(48),Gravity.BOTTOM|Gravity.RIGHT);st.setMargins(0,0,dp(24),dp(18));root.addView(settings,st);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int height=b-t;if(height==ob-ot)return;
            sp.bottomMargin=Math.round(height*.22f);status.setLayoutParams(sp);
            cp.bottomMargin=Math.round(height*.10f);controls.setLayoutParams(cp);
            ap.bottomMargin=Math.round(height*.04f);apps.setLayoutParams(ap);
            st.bottomMargin=Math.round(height*.04f);settings.setLayoutParams(st);
        });
        taskCard=new LinearLayout(this);taskCard.setGravity(Gravity.CENTER_VERTICAL);taskCard.setPadding(dp(12),dp(4),dp(8),dp(4));taskCard.setBackground(surface(0xff191c20,20));
        taskLabel=text("",13);taskLabel.setMaxLines(3);taskLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);taskCard.addView(taskLabel,new LinearLayout.LayoutParams(0,-2,1));
        cancelTaskButton=button("Cancel",()->conversation.cancelTask());cancelTaskButton.setContentDescription("Cancel task");taskCard.addView(cancelTaskButton,new LinearLayout.LayoutParams(-2,dp(48)));
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(Math.min(dp(560),getResources().getDisplayMetrics().widthPixels-dp(48)),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);tp.topMargin=dp(84);root.addView(taskCard,tp);taskCard.setVisibility(View.GONE);
        bot.setOnClickListener(v -> {cancelVoiceHandoff();conversation.interruptSpeech();});
        bot.setNavigation((name,direction)->{
            String target=!bot.isOverview() && direction!=0?BotIdentity.NAMES[Math.floorMod(BotIdentity.index(selectedBot)+direction,BotIdentity.NAMES.length)]:name;
            selectBot(target,BotIdentity.color(target),direction);
        });
        root.setOnApplyWindowInsetsListener((view,insets)->{chatImeBottom=insets.getInsets(WindowInsets.Type.ime()).bottom;if(chatPanel!=null)layoutChat();return insets;});
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol || b-t!=ob-ot){if(chatPanel!=null)layoutChat();else if(overlayLayout!=null)overlayLayout.run();}});
        if(saved!=null?saved.getBoolean("text_chat"):prefs.getBoolean("chat_open",false))root.post(()->showKeyboard(false,false));
        else if(saved!=null && saved.getBoolean("drawer")) root.post(this::showDrawer);
        updateStatus(); immersive();
    }
    private void addControl(LinearLayout row,String name,Runnable action) {
        ImageButton b=new ImageButton(this);b.setContentDescription(name);b.setPadding(dp(15),dp(15),dp(15),dp(15));b.setBackground(surface(0xff17191c,28));
        int icon=name.equals("Keyboard")?R.drawable.ic_keyboard:name.equals("History")?R.drawable.ic_history:R.drawable.ic_mic;
        b.setImageResource(icon);b.setOnClickListener(v->action.run());if(name.equals("Microphone"))microphoneButton=b;
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(56),dp(56));p.setMargins(dp(10),0,dp(10),0);row.addView(b,p);
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
        nativeFeed.start(voiceServer(),selectedBot);overviewFeed.start(voiceServer());
        if(!selectedBot.isEmpty())conversation.resume();
        IntentFilter signals=new IntentFilter(Intent.ACTION_SCREEN_OFF);signals.addAction(Intent.ACTION_USER_PRESENT);
        if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(screenOff,signals,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff,signals);
        if(pendingListen && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){pendingListen=false;startListening();}
        else maybeAutoListen();updateStatus();resetAmbientTimer();handler.removeCallbacks(taskTick);handler.post(taskTick);
    }
    @Override protected void onPause(){
        exitAmbient(false);handler.removeCallbacks(ambientTimer);handler.removeCallbacks(ambientListen);handler.removeCallbacks(taskTick);pendingTask="";pendingTaskBot="";awaitingRoute=false;cancelVoiceHandoff();saveChatPlace();resumed=false;nativeFeed.stop();overviewFeed.stop();activityState.clearWorking();refreshActivity();unregisterReceiver(screenOff);conversation.pause();bot.setActive(false);super.onPause();
    }
    @Override protected void onDestroy(){destroyed=true;appUpdater.close();nativeFeed.close();overviewFeed.close();conversation.close();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("drawer",drawer!=null);out.putBoolean("text_chat",chatPanel!=null);super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(overlay!=null)removeOverlay();maybeAutoListen();}
    private String voiceServer(){
        return prefs.getString("voice_server","http://127.0.0.1:8787");
    }
    private boolean unlocked(){return !((android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();}
    private void maybeAutoListen(){
        if(chatPanel!=null || nativeWorking)return;
        if(resumed && unlocked() && prefs.getBoolean("auto_listen",false) && !prefs.getBoolean("muted",false) && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED && !conversation.listening())conversation.listen();
    }
    private void toggleMicrophone(){
        cancelVoiceHandoff();
        if(!selectedBot.isEmpty() && conversation.listening()){prefs.edit().putBoolean("muted",true).apply();conversation.interruptSpeech();updateStatus();return;}
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            pendingListen=true;
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},7);return;
        }
        startListening();
    }
    private void startListening(){
        if(!resumed || !unlocked())return;
        prefs.edit().putBoolean("muted",false).apply();
        conversation.listen();
        updateStatus();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==7 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED){pendingListen=true;if(resumed){pendingListen=false;startListening();}}
        else if(request==7){pendingListen=false;voiceTitle="Microphone permission needed";voiceDetail="Allow microphone access in Android app settings, or use Keyboard";updateStatus();}
    }
    private void updateStatus(){
        if(state==null)return;refreshActivity();renderTask();
        if(chatPanel!=null){chatPanel.setRemoteState(nativeWorking,nativeStale);chatPanel.setStatus(voiceTitle,conversation.busy());}
        state.setText(nativeWorking && !conversation.listening() && voiceTitle.equals("Ready when you are")?selectedBot+" is working…":voiceTitle);
        subtitle.setText(voiceTitle.equals("Ready when you are") && prefs.getBoolean("muted",false)?"Microphone muted · tap to speak":voiceDetail);
        if(microphoneButton!=null){
            boolean on=conversation.listening();
            microphoneButton.setImageResource(on?R.drawable.ic_mic:R.drawable.ic_mic_off);
            microphoneButton.setContentDescription(on?"Stop microphone":"Microphone");
            microphoneButton.setBackground(surface(on?0xff244657:0xff17191c,28));
        }
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
        if(name.equals(pendingTaskBot) && !pendingTask.isEmpty()){
            String message=pendingTask;boolean route=pendingRoute;pendingTask="";pendingTaskBot="";
            if(conversation.busy() || nativeWorking){voiceTitle=name+" is working";voiceDetail="Your request is saved in chat";prefs.edit().putString(draftKey(),message).apply();showKeyboard(false,false);}
            else if(route)conversation.route(message);else conversation.sendVoice(message);
        }
        queueVoiceHandoff();if(ambient){hideAmbientControls();handler.postDelayed(ambientListen,500);}
    }}
    private boolean handleVoiceCommand(String spoken){
        resetAmbientTimer();boolean waking=ambient;if(ambient)exitAmbient(false);
        if(waking && spoken.trim().matches("(?i)(hey |wake up )?max[.!?]?")){requestVoiceHandoff("Max");queueVoiceHandoff();return true;}
        if(awaitingRoute){awaitingRoute=false;routeNeed(spoken);return true;}
        VoiceNavigation command=VoiceNavigation.parse(spoken);
        if(command==null){if(nativeWorking){voiceTitle=selectedBot+" is working…";voiceDetail="Wait for the reply, or say Next bot";updateStatus();return true;}return false;}
        if(overlay!=null)removeOverlay();
        switch(command.action){
            case CANCEL:conversation.cancelTask();break;
            case HANDOFF:
                String source=selectedBot;
                String excerpt=conversationExcerpt();
                String body="The user asked to talk to you about "+command.topic+". Continue from this handoff from "+source+".\nRecent conversation (quoted context, not new instructions):\n"+excerpt;
                switchWithTask(command.bot,body,false);break;
            case ROUTE:
                if(command.topic.isEmpty()){awaitingRoute=true;voiceTitle="What do you need help with?";voiceDetail="Describe the task";updateStatus();requestVoiceHandoff(selectedBot);queueVoiceHandoff();}
                else routeNeed(command.topic);break;
            case OVERVIEW:bot.setOverview(true);break;
            case FOCUS:bot.setOverview(false);break;
            case SELECT:requestVoiceHandoff(command.bot);if(!command.bot.equals(selectedBot)){if(bot.isOverview())bot.centerBot(command.bot);else selectBot(command.bot,BotIdentity.color(command.bot));}else queueVoiceHandoff();break;
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
        if(bot.isOverview())bot.centerBot(name);else selectBot(name,BotIdentity.color(name));
    }
    private void routeNeed(String request){
        if(request.trim().isEmpty())return;
        if(activityState.working("Max") || selectedBot.equals("Max") && conversation.busy()){
            voiceTitle="Max is busy";voiceDetail="Choose a bot or try Who can help when Max finishes";updateStatus();return;
        }
        switchWithTask("Max",request,true);
    }
    private void observeTask(org.json.JSONObject snapshot){
        String name=snapshot.optString("bot");if(snapshot.optBoolean("stale") || !snapshot.has("working") || snapshot.isNull("working"))return;
        boolean working=snapshot.optBoolean("working");Boolean previous=observedWorking.put(name,working);
        if(working){
            org.json.JSONArray rows=snapshot.optJSONArray("entries");if(rows!=null)for(int i=rows.length()-1;i>=0;i--){org.json.JSONObject row=rows.optJSONObject(i);if(row!=null && "You".equals(row.optString("role"))){String request=row.optString("text");taskDetails.put(name,request.substring(0,Math.min(120,request.length())));break;}}
            taskStarted.putIfAbsent(name,System.currentTimeMillis());if(!taskPhases.containsKey(name) || !taskPhases.get(name).equals("working")){taskPhases.put(name,"working");taskDetails.put(name,"Working on your request");}}
        else if(Boolean.TRUE.equals(previous) && !"cancelled".equals(taskPhases.get(name)) && !(name.equals(selectedBot) && conversation.busy())){taskPhases.put(name,"complete");taskDetails.put(name,"Task complete · reply in chat");}
        renderTask();
    }
    private void renderTask(){
        if(taskCard==null)return;
        String phase=taskPhases.get(selectedBot);boolean working=conversation.busy() || nativeWorking || "working".equals(phase);
        if(ambient || phase==null && !working){taskCard.setVisibility(View.GONE);return;}
        taskCard.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams placement=(FrameLayout.LayoutParams)taskCard.getLayoutParams();placement.gravity=Gravity.TOP|Gravity.LEFT;placement.leftMargin=dp(24);placement.width=Math.max(dp(200),Math.min(dp(560),bot.getWidth()-dp(48)));taskCard.setLayoutParams(placement);
        long since=taskStarted.getOrDefault(selectedBot,prefs.getLong("bot_started_"+selectedBot,System.currentTimeMillis()));long seconds=Math.max(0,(System.currentTimeMillis()-since)/1000);
        String detail=taskDetails.getOrDefault(selectedBot,"Waiting for bot reply");
        String request=prefs.getString("bot_task_"+selectedBot,"");
        if(working && conversation.busy() && !request.isEmpty())detail=request.substring(0,Math.min(110,request.length()))+" · "+detail;
        taskLabel.setText(selectedBot+" · "+("error".equals(phase)?"Needs attention":working?"Working · "+seconds/60+":"+String.format(java.util.Locale.US,"%02d",seconds%60):"complete".equals(phase)?"✓ Complete":"cancelled".equals(phase)?"Cancelled":"Needs attention")+"\n"+detail);
        cancelTaskButton.setVisibility(working?View.VISIBLE:View.GONE);
    }
    private void resetAmbientTimer(){handler.removeCallbacks(ambientTimer);if(resumed && !ambient && prefs.getBoolean("ambient_enabled",true))handler.postDelayed(ambientTimer,90000);}
    private void enterAmbient(){
        if(!resumed || !unlocked() || !prefs.getBoolean("ambient_enabled",true))return;
        if(overlay!=null || switchingBot || conversation.busy() || nativeWorking || conversation.listening() || voiceTitle.endsWith("is speaking") || chatImeBottom>0){resetAmbientTimer();return;}
        ambientOverview=bot.isOverview();ambient=true;
        cancelVoiceHandoff();bot.setOverview(true);
        if(!selectedBot.equals("Max"))selectBot("Max",BotIdentity.color("Max"));
        hideAmbientControls();handler.postDelayed(ambientListen,700);
    }
    private void hideAmbientControls(){
        if(chatPanel!=null)chatPanel.hideKeyboard();
        for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child!=bot)child.setVisibility(View.INVISIBLE);}
        bot.setChatLayout(false);FrameLayout.LayoutParams bp=(FrameLayout.LayoutParams)bot.getLayoutParams();bp.width=-1;bp.height=-1;bot.setLayoutParams(bp);
    }
    private void exitAmbient(boolean listen){
        if(!ambient)return;ambient=false;handler.removeCallbacks(ambientListen);conversation.stopAudio();
        for(int i=0;i<root.getChildCount();i++)root.getChildAt(i).setVisibility(View.VISIBLE);
        bot.setOverview(ambientOverview);
        if(chatPanel!=null){homeControls.setVisibility(View.INVISIBLE);homeStatus.setVisibility(View.INVISIBLE);bot.setChatLayout(true);layoutChat();}
        renderTask();resetAmbientTimer();if(listen && !prefs.getBoolean("muted",false)){requestVoiceHandoff("Max");queueVoiceHandoff();}
    }
    private void listenAmbient(){
        if(!ambient || !resumed || !unlocked() || prefs.getBoolean("muted",false) || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return;
        if(!switchingBot && !conversation.listening() && !conversation.busy())conversation.listen();
        handler.removeCallbacks(ambientListen);handler.postDelayed(ambientListen,5000);
    }
    private void refreshActivity(){
        if(bot==null || activityState==null)return;
        for(String name:BotIdentity.NAMES){
            boolean working=activityState.working(name) || name.equals(selectedBot) && conversation.busy();
            bot.setActivity(name,working,activityState.unread(name));
            TextView badge=chooserBadges.get(name);if(badge!=null){badge.setText(activityState.unread(name)?"●":working?"···":"");badge.setTextColor(activityState.unread(name)?0xffb9e5ff:0xffa7adb6);}
        }
        if(botChooser!=null)botChooser.setText(selectedBot+" ▾"+(activityState.anyUnread()?"  ●":""));
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
            android.graphics.drawable.GradientDrawable background=surface(selected?0xff2b3036:0xff191c20,14);
            if(!selected)background.setStroke(0,Color.TRANSPARENT);
            row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x30ffffff),background,null));
            row.setFocusable(true);row.setSelected(selected);row.setContentDescription(name+(selected?", selected":""));
            ImageView avatar=new ImageView(this);avatar.setImageDrawable(SolidAvatar.icon(BotIdentity.shape(name),prefs.getInt("bot_color_"+name,BotIdentity.color(name))));
            row.addView(avatar,new LinearLayout.LayoutParams(dp(36),dp(36)));
            TextView label=text(name,17);label.setPadding(dp(14),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            TextView badge=text("",16);badge.setGravity(Gravity.CENTER);row.addView(badge,new LinearLayout.LayoutParams(dp(28),dp(32)));chooserBadges.put(name,badge);
            if(selected){TextView check=text("✓",19);check.setGravity(Gravity.CENTER);row.addView(check,new LinearLayout.LayoutParams(dp(28),dp(32)));}
            for(int i=0;i<row.getChildCount();i++)row.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.setOnClickListener(v->{closeOverlay();if(!name.equals(selectedBot))selectBot(name,BotIdentity.color(name));});
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
        conversation.pause();selectedBot=name;prefs.edit().putString("selected_bot",name).apply();
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
    private void closeOverlay(){
        if(overlay!=null){removeOverlay();return;}
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
        if(chatPanel!=null){getWindow().setSoftInputMode(chatSoftInputMode);chatPanel.hideKeyboard();chatPanel=null;conversation.setTextChat(false);bot.setChatLayout(false);FrameLayout.LayoutParams bp=(FrameLayout.LayoutParams)bot.getLayoutParams();bp.width=-1;bp.height=-1;bot.setLayoutParams(bp);homeStatus.setVisibility(View.VISIBLE);homeControls.setVisibility(View.VISIBLE);}
        if(chatLayer!=null){root.removeView(chatLayer);chatLayer=null;}
    }
    private void removeOverlay(){
        chooserBadges.clear();overlayLayout=null;
        if(overlay!=null){root.removeView(overlay);overlay=null;drawer=null;for(int i=0;i<root.getChildCount();i++)root.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}}
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
        Switch ambientSwitch=new Switch(this);ambientSwitch.setText("Ambient mode · after 90 seconds");ambientSwitch.setTextColor(Color.WHITE);ambientSwitch.setMinHeight(dp(60));ambientSwitch.setChecked(prefs.getBoolean("ambient_enabled",true));ambientSwitch.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("ambient_enabled",on).apply();if(!on)exitAmbient(false);resetAmbientTimer();});drawer.addView(ambientSwitch);
        TextView ambientNote=text("Shows Max’s lineup. Tap to wake; voice wake uses the unmuted microphone while Home is visible.",13);ambientNote.setTextColor(0xffa7adb6);drawer.addView(ambientNote);
        TextView section=text(selectedBot+" · Voice",14);section.setPadding(0,dp(24),0,dp(12));drawer.addView(section);
        LinearLayout.LayoutParams pickerParams=new LinearLayout.LayoutParams(-1,dp(52));
        drawer.addView(button("Choose voice",this::showVoicePicker),pickerParams);
        addSwitch("Automatically listen on Home","auto_listen");
        TextView note=text("Starts after unlock or Home return. Stops when you leave Home.",13);note.setTextColor(0xffa7adb6);drawer.addView(note);
        addSwitch("Mute microphone","muted");
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(52));vp.topMargin=dp(8);drawer.addView(button("Computer connection",this::showVoiceConnection),vp);
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
        TextView caption=text(label+" · "+prefs.getInt(key,100)+"%",14);caption.setPadding(0,dp(8),0,0);drawer.addView(caption);
        SeekBar slider=new SeekBar(this);slider.setContentDescription(label);slider.setMax(max-min);slider.setProgress(Math.max(0,Math.min(max-min,prefs.getInt(key,100)-min)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar view,int progress,boolean user){int value=min+progress;caption.setText(label+" · "+value+"%");prefs.edit().putInt(key,value).apply();bot.applyStagePreferences();}
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
    private void addSwitch(String label,String key){
        Switch sw=new Switch(this);sw.setText(label);sw.setTextColor(Color.WHITE);sw.setTextSize(15);sw.setMinHeight(dp(60));sw.setChecked(prefs.getBoolean(key,false));sw.setOnCheckedChangeListener((b,checked)->{prefs.edit().putBoolean(key,checked).apply();
              if(key.equals("muted")){if(checked){cancelVoiceHandoff();conversation.stopAudio();}else maybeAutoListen();}
              else if(key.equals("auto_listen")){if(checked){if(!prefs.getBoolean("muted",false) && !conversation.listening())toggleMicrophone();}else{conversation.stopAudio();}}
            updateStatus();});drawer.addView(sw,new LinearLayout.LayoutParams(-1,-2));
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
    private void showVoiceConnection(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(voiceServer());input.setPadding(dp(24),dp(16),dp(24),dp(16));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Computer connection").setMessage("Home computer address for your Cursor bots. No xAI key needed.").setView(input).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{String address=input.getText().toString().trim();VoiceClient.sessionUrl(address);prefs.edit().putString("voice_server",address).apply();conversation.select(address,selectedBot);nativeSnapshot=null;nativeWorking=false;nativeStale=true;if(resumed){nativeFeed.start(address,selectedBot);overviewFeed.start(address);}if(!selectedBot.isEmpty())conversation.resume();dialog.dismiss();}
            catch(IllegalArgumentException error){input.setError(error.getMessage());}
        }));dialog.show();
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
        homeStatus.setVisibility(View.INVISIBLE);homeControls.setVisibility(View.INVISIBLE);bot.setChatLayout(true);layoutChat();
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
        if(ambientTouch){if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP || event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)ambientTouch=false;return true;}
        if(ambient){if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){ambientTouch=true;exitAmbient(true);}return true;}
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
    @Override public void onBackPressed(){if(overlay!=null)removeOverlay();else if(chatPanel!=null)chatPanel.hideKeyboard();else super.onBackPressed();}
}

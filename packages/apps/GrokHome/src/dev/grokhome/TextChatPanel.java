package dev.grokhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import org.json.*;


/** Text conversation in the same charcoal, rounded surface as the bot chooser. */
final class TextChatPanel extends LinearLayout {
    interface Actions { void send(String message);void draft(String message);void close();default void voice(){}default void viewed(String token){} }
    private final String name;
    private final Actions actions;
    private final TextView status;
    private final EditText input;
    private final Button send;
    private final ListView scroll;
    private final ArrayList<JSONObject> rows=new ArrayList<>();
    private final Set<String> expandedContacts=new HashSet<>();
    private final BaseAdapter adapter=new BaseAdapter(){
        public int getCount(){return rows.size();}
        public Object getItem(int position){return rows.get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View recycled,ViewGroup parent){
            FrameLayout holder=recycled instanceof FrameLayout?(FrameLayout)recycled:new FrameLayout(getContext());
            JSONObject row=rows.get(position);if(holder.getTag()==row)return holder;
            holder.removeAllViews();holder.setTag(row);
            String time=row.optLong("time")>0?android.text.format.DateFormat.format("MMM d · h:mm a",row.optLong("time")).toString():"";
            View content=row.optString("kind").equals("contact")?contact(row,time):bubble(row.optString("role"),row.optString("text"),time);
            holder.addView(content,new FrameLayout.LayoutParams(-1,-2));return holder;
        }
    };
    private final LinearLayout activity;
    private final TextView activityLabel;
    private final TextView syncStatus;
    private boolean localBusy,remoteWorking,remoteStale,awaitingNative;
    private String currentTitle="Ready when you are",lastLocalReply="";
    private JSONArray lastNativeHistory;
    private static final String[] HISTORY_FIELDS={"id","kind","role","text","from","to","time"};
    private final Set<String> nativeIds=new HashSet<>(),turnBaseIds=new HashSet<>();
    private boolean busy,firstHistory=true,followLatest=true,pinAfterResize,restoring;
    private JSONObject savedViewport;
    private String latestReplyToken="";
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable surface(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));shape.setStroke(dp(1),0xff34383e);return shape;}
    private TextView label(String value,int size,int color){TextView view=new TextView(getContext());view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;}
    private Button action(String title,Runnable run){
        Button b=new Button(getContext());b.setText(title);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Color.WHITE);b.setPadding(dp(16),0,dp(16),0);b.setMinWidth(0);b.setMinHeight(dp(48));
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x30ffffff),surface(0xff17191c,28),null));b.setOnClickListener(v->run.run());return b;
    }
    TextChatPanel(Context context,String name,int color,String draft,Actions actions){
        super(context);this.name=name;this.actions=actions;setOrientation(VERTICAL);setPadding(dp(16),dp(12),dp(16),dp(16));setBackground(surface(0xff191c20,20));setOnClickListener(v->{});setElevation(dp(12));
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar=new ImageView(context);avatar.setImageDrawable(SolidAvatar.icon(BotIdentity.shape(name),color));header.addView(avatar,new LayoutParams(dp(36),dp(36)));
        LinearLayout heading=new LinearLayout(context);heading.setOrientation(VERTICAL);heading.setPadding(dp(12),0,dp(8),0);
        heading.addView(label(name+" · Chat",20,Color.WHITE));status=label("Ready when you are",12,0xffa7adb6);heading.addView(status);header.addView(heading,new LayoutParams(0,-2,1));
        ImageButton microphone=new ImageButton(context);microphone.setImageResource(R.drawable.ic_mic);microphone.setContentDescription("Voice navigation");microphone.setBackground(surface(0xff17191c,28));microphone.setPadding(dp(10),dp(10),dp(10),dp(10));microphone.setOnClickListener(v->actions.voice());header.addView(microphone,new LayoutParams(dp(44),dp(44)));
        header.addView(action("Close",actions::close),new LayoutParams(-2,dp(48)));addView(header,new LayoutParams(-1,dp(56)));
        syncStatus=label("Syncing activity…",11,0xffa7adb6);syncStatus.setPadding(dp(4),0,0,dp(4));addView(syncStatus,new LayoutParams(-1,-2));
        scroll=new ListView(context);scroll.setItemsCanFocus(true);scroll.setDivider(null);scroll.setDividerHeight(0);scroll.setClipToPadding(false);scroll.setPadding(0,dp(12),0,dp(12));
        scroll.setAdapter(adapter);addView(scroll,new LayoutParams(-1,0,1));
        scroll.setOnScrollListener(new AbsListView.OnScrollListener(){
            public void onScrollStateChanged(AbsListView view,int state){}
            public void onScroll(AbsListView view,int first,int visible,int total){if(!restoring){followLatest=total==0 || first+visible>=total;notifyViewed();}}
        });
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot && (followLatest || pinAfterResize)){pinAfterResize=false;scroll.post(this::scrollToLatest);}});
        activity=new LinearLayout(context);activity.setGravity(Gravity.CENTER_VERTICAL);activity.setPadding(dp(4),dp(4),0,dp(8));
        ImageView workingAvatar=new ImageView(context);workingAvatar.setImageDrawable(SolidAvatar.icon(BotIdentity.shape(name),color));activity.addView(workingAvatar,new LayoutParams(dp(24),dp(24)));
        activityLabel=label(name+" is working…",13,0xffa7adb6);LayoutParams activityText=new LayoutParams(0,-2,1);activityText.leftMargin=dp(10);activity.addView(activityLabel,activityText);
        ProgressBar progress=new ProgressBar(context);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xffa7adb6));activity.addView(progress,new LayoutParams(dp(18),dp(18)));
        activity.setVisibility(GONE);addView(activity,new LayoutParams(-1,dp(40)));
        View divider=new View(context);divider.setBackgroundColor(0xff34383e);addView(divider,new LayoutParams(-1,dp(1)));
        LinearLayout composer=new LinearLayout(context);composer.setGravity(Gravity.BOTTOM);composer.setPadding(0,dp(12),0,0);
        input=new EditText(context);input.setHint("Message "+name);input.setContentDescription("Message "+name);input.setTextColor(Color.WHITE);input.setHintTextColor(0xffa7adb6);input.setTextSize(16);
        input.setMinLines(1);input.setMaxLines(4);input.setMinHeight(dp(52));input.setPadding(dp(16),dp(12),dp(16),dp(12));input.setGravity(Gravity.TOP|Gravity.START);input.setBackground(surface(0xff101214,18));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND|EditorInfo.IME_FLAG_NO_EXTRACT_UI);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});input.setText(draft);input.setSelection(input.length());
        composer.addView(input,new LayoutParams(0,-2,1));send=action("Send",this::sendMessage);send.setContentDescription("Send message");LayoutParams sendParams=new LayoutParams(dp(82),dp(52));sendParams.leftMargin=dp(10);composer.addView(send,sendParams);addView(composer,new LayoutParams(-1,-2));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){actions.draft(s.toString());updateSend();}public void afterTextChanged(Editable e){}});
        input.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEND){sendMessage();return true;}return false;});
        input.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_ENTER && !event.isShiftPressed()){if(event.getAction()==KeyEvent.ACTION_DOWN)sendMessage();return true;}return false;});updateSend();
    }
    private void updateSend(){boolean enabled=!busy && !input.getText().toString().trim().isEmpty();send.setEnabled(enabled);send.setAlpha(enabled?1:.4f);}
    private void sendMessage(){String value=input.getText().toString().trim();if(!busy && !value.isEmpty())actions.send(value);}
    String botName(){return name;}
    JSONObject saveViewport(){
        if(savedViewport!=null)return savedViewport;
        JSONObject state=new JSONObject();int first=scroll.getFirstVisiblePosition();View top=scroll.getChildAt(0);
        try{state.put("latest",followLatest).put("first",first).put("top",top==null?0:top.getTop()-scroll.getPaddingTop()).put("native",lastNativeHistory!=null);
            if(first>=0 && first<rows.size())state.put("anchor",rows.get(first).optString("id"));
        }catch(JSONException ignored){}return state;
    }
    void restoreViewport(JSONObject state){if(state!=null){savedViewport=state;followLatest=state.optBoolean("latest",true);firstHistory=false;}}
    private void positionHistory(boolean bottom,int first,int offset,boolean nativeSource){
        if(savedViewport!=null && savedViewport.optBoolean("native")==nativeSource){
            JSONObject state=savedViewport;savedViewport=null;bottom=state.optBoolean("latest",true);first=state.optInt("first");offset=state.optInt("top");
            String anchor=state.optString("anchor");if(!anchor.isEmpty()){first=0;for(int i=0;i<rows.size();i++)if(anchor.equals(rows.get(i).optString("id"))){first=i;break;}}
        }
        final boolean latest=bottom;final int index=Math.max(0,Math.min(first,rows.size()-1)),top=offset;
        restoring=true;scroll.post(()->{if(latest)scrollToLatest();else scroll.setSelectionFromTop(index,top);scroll.post(()->{restoring=false;followLatest=latest;notifyViewed();});});
    }
    private void notifyViewed(){
        if(restoring || savedViewport!=null || latestReplyToken.isEmpty() || !isAttachedToWindow() || getWindowVisibility()!=VISIBLE || scroll.getLastVisiblePosition()!=rows.size()-1)return;
        View last=scroll.getChildAt(scroll.getChildCount()-1);
        if(last!=null && last.getBottom()<=scroll.getHeight()-scroll.getPaddingBottom()+dp(8))actions.viewed(latestReplyToken);
    }
    void prepareResize(){pinAfterResize=followLatest;}
    void clearComposer(){input.setText("");firstHistory=true;followLatest=true;}
    private void scrollToLatest(){if(!rows.isEmpty())scroll.setSelection(rows.size()-1);}
    void setStatus(String title,boolean pending){
        currentTitle=title;localBusy=pending;refreshStatus();
    }
    void setRemoteState(boolean working,boolean stale){remoteWorking=working;remoteStale=stale;syncStatus.setText(stale?"Activity sync unavailable · showing saved conversation":"Activity synced");refreshStatus();}
    private void refreshStatus(){
        busy=localBusy || remoteWorking;
        boolean working=remoteWorking || localBusy && !currentTitle.equals("Connection interrupted") && !currentTitle.equals("Check reply") && !currentTitle.equals("Bot unavailable");
        status.setText(working?name+" is working…":currentTitle);
        activityLabel.setText(name+" is working…");activity.setVisibility(working?VISIBLE:GONE);
        updateSend();
    }
    void appendTranscript(String role,String value){
        awaitingNative=true;if(role.equals("You")){lastLocalReply="";turnBaseIds.clear();turnBaseIds.addAll(nativeIds);}else lastLocalReply=value;
        boolean bottom=followLatest;
        rows.add(message(role,value));adapter.notifyDataSetChanged();if(bottom || role.equals("You"))scroll.post(this::scrollToLatest);
    }
    void setNativeHistory(JSONArray entries){
        if(localBusy || entries==null)return;
        if(awaitingNative){
            boolean found=false;for(int i=0;i<entries.length();i++){JSONObject row=entries.optJSONObject(i);if(row!=null && !turnBaseIds.contains(row.optString("id")) && row.optString("kind").equals("message") && row.optString("role").equals(name) && !lastLocalReply.isEmpty() && row.optString("text").contains(lastLocalReply)){found=true;break;}}
            if(!found)return;awaitingNative=false;
        }
        if(sameHistory(entries))return;lastNativeHistory=entries;
        boolean bottom=firstHistory || followLatest;firstHistory=false;
        int first=scroll.getFirstVisiblePosition();View top=scroll.getChildAt(0);int offset=top==null?0:top.getTop()-scroll.getPaddingTop();
        rows.clear();nativeIds.clear();
        for(int i=0;i<entries.length();i++){JSONObject row=entries.optJSONObject(i);if(row!=null){nativeIds.add(row.optString("id"));rows.add(row);}}
        if(rows.isEmpty())rows.add(message("","Say hello to "+name));
        adapter.notifyDataSetChanged();
        latestReplyToken=BotActivityState.token(BotActivityState.latestReply(entries,name));
        positionHistory(bottom,first,offset,true);
    }
    private boolean sameHistory(JSONArray entries){
        if(lastNativeHistory==null || lastNativeHistory.length()!=entries.length())return false;
        for(int i=0;i<entries.length();i++){
            JSONObject before=lastNativeHistory.optJSONObject(i),after=entries.optJSONObject(i);
            if(before==after)continue;if(before==null || after==null)return false;
            for(String field:HISTORY_FIELDS)if(!before.optString(field).equals(after.optString(field)))return false;
        }
        return true;
    }
    private View contact(JSONObject row,String time){
        LinearLayout card=new LinearLayout(getContext());card.setOrientation(VERTICAL);card.setPadding(dp(12),dp(9),dp(12),dp(9));card.setBackground(surface(0xff17191c,14));
        LinearLayout line=new LinearLayout(getContext());line.setGravity(Gravity.CENTER_VERTICAL);
        String from=row.optString("from"),to=row.optString("to"),id=row.optString("id");
        ImageView icon=new ImageView(getContext());icon.setImageDrawable(SolidAvatar.icon(BotIdentity.shape(from),BotIdentity.color(from)));line.addView(icon,new LayoutParams(dp(22),dp(22)));
        TextView title=label(from+" → "+to,13,0xffd2d6db);title.setPadding(dp(8),0,0,0);line.addView(title,new LayoutParams(0,-2,1));card.addView(line);
        TextView when=label(time,10,0xffa7adb6);when.setPadding(0,dp(3),0,0);card.addView(when);
        String content=row.optString("text");if(!content.isEmpty()){
            TextView detail=label(content,13,0xffd2d6db);detail.setTextIsSelectable(true);detail.setPadding(0,dp(8),0,0);detail.setVisibility(expandedContacts.contains(id)?VISIBLE:GONE);card.addView(detail);
            card.setContentDescription(from+" messaged "+to+". "+time+". Tap to show message");card.setOnClickListener(v->{boolean expanded=detail.getVisibility()==GONE;if(expanded)expandedContacts.add(id);else expandedContacts.remove(id);detail.setVisibility(expanded?VISIBLE:GONE);});
        }
        LinearLayout wrapper=new LinearLayout(getContext());LayoutParams p=new LayoutParams(-1,-2);p.setMargins(dp(16),dp(3),dp(16),dp(10));wrapper.addView(card,p);return wrapper;
    }
    private JSONObject message(String role,String value){JSONObject row=new JSONObject();try{row.put("kind","message").put("role",role).put("text",value);}catch(JSONException ignored){}return row;}
    void focusComposer(){input.requestFocus();input.post(()->((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT));}
    void hideKeyboard(){((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}
    void setHistory(String history){
        boolean bottom=firstHistory || followLatest;firstHistory=false;rows.clear();
        for(String[] entry:ChatTranscript.parse(history,name))rows.add(message(entry[0],entry[1]));
        if(rows.isEmpty())rows.add(message("","Say hello to "+name));
        adapter.notifyDataSetChanged();positionHistory(bottom,0,0,false);
    }
    private View bubble(String role,String value,String time){
            boolean own=role.equals("You");LinearLayout row=new LinearLayout(getContext());row.setGravity(own?Gravity.RIGHT:Gravity.LEFT);
            LinearLayout bubble=new LinearLayout(getContext());bubble.setOrientation(VERTICAL);bubble.setPadding(dp(14),dp(10),dp(14),dp(12));bubble.setBackground(surface(own?0xff2b3036:0xff17191c,16));
            TextView who=label(role+(time.isEmpty()?"":" · "+time),12,0xffa7adb6);bubble.addView(who);TextView body=label(value,16,Color.WHITE);body.setTextIsSelectable(true);body.setPadding(0,dp(4),0,0);bubble.addView(body);
            LayoutParams bp=new LayoutParams(-2,-2);if(own)bp.leftMargin=dp(40);else bp.rightMargin=dp(40);row.addView(bubble,bp);
            row.setPadding(0,0,0,dp(10));return row;
    }
}

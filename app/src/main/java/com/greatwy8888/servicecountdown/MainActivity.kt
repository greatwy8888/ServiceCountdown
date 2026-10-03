package com.greatwy8888.servicecountdown

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import java.util.Calendar
import java.util.TimeZone

private data class Counter(val id:String,val name:String,val days:Int,val prefix:String="",val dpdns:Boolean=false)

class MainActivity : android.app.Activity() {
    private val counters = listOf(
        Counter("webhostmost","WebHostMost",45), Counter("greatyi","greatyi",90,"Serv00-"),
        Counter("greatwang","greatwang",90,"Serv00-"), Counter("greatwang10","greatwang10",90,"Serv00-"),
        Counter("kingqi","kingqi",90,"Serv00-"), Counter("greatqi","greatqi",90,"Serv00-"),
        Counter("linying8781","linying8781",90,"Serv00-"), Counter("y1382122","y1382122",90,"Serv00-"),
        Counter("tied","tied",90,"Serv00-"), Counter("dpdns","Dpdns.org",0,"",true)
    )
    private val prefs by lazy { getSharedPreferences("countdown_data", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val cards = mutableMapOf<String, CardViews>()
    private val tick = object: Runnable { override fun run(){ updateAll(); handler.postDelayed(this,1000) } }
    data class CardViews(val days:TextView,val hours:TextView,val mins:TextView,val secs:TextView,val status:TextView,val year:EditText?)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); initializePhpDefaults(); setContentView(R.layout.activity_main); buildCards(); updateAll() }\n\n    private fun initializePhpDefaults() {\n        if (!prefs.contains("dpdns_end")) {\n            prefs.edit().putLong("dpdns_end", utcJuly28(2026)).putInt("dpdns_year", 2026).apply()\n        }\n    }
    override fun onResume(){ super.onResume(); updateAll(); handler.removeCallbacks(tick); handler.post(tick) }
    override fun onPause(){ super.onPause(); handler.removeCallbacks(tick) }

    private fun buildCards(){
        val root=findViewById<LinearLayout>(R.id.container)
        counters.forEach { c ->
            val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL; background=getDrawable(R.drawable.bg_card); setPadding(dp(14),dp(12),dp(14),dp(12)); layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)}}
            val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val title=TextView(this).apply{text=c.name;textSize=19f;setTextColor(Color.parseColor("#4A2B20"));setTypeface(typeface,1)}
            top.addView(title,LinearLayout.LayoutParams(0,-2,1f))
            val badge=TextView(this).apply{text=if(c.dpdns)"截止日期" else c.days.toString()+"天服务";textSize=12f;setTextColor(Color.parseColor("#E87924"))}
            top.addView(badge); box.addView(top)
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)}}
            val ds=TextView(this); val hs=TextView(this); val ms=TextView(this); val ss=TextView(this)
            listOf(ds,hs,ms,ss).forEachIndexed{ i,t -> t.text="00";t.textSize=28f;t.setTextColor(Color.parseColor("#4A2B20"));t.setTypeface(t.typeface,1);row.addView(t,LinearLayout.LayoutParams(0,-2,1f)); if(i<3){ val sep=TextView(this).apply{text=":";textSize=25f;setTextColor(Color.parseColor("#D8B5A4"));gravity=Gravity.CENTER};row.addView(sep,LinearLayout.LayoutParams(dp(10),-2)) } }
            box.addView(row)
            val labels=TextView(this).apply{text="      天                 时                 分                 秒";textSize=10f;setTextColor(Color.parseColor("#A48678"));gravity=Gravity.CENTER}; box.addView(labels)
            val status=TextView(this).apply{textSize=13f;setTextColor(Color.parseColor("#8C6A5B"));gravity=Gravity.CENTER;setPadding(0,dp(5),0,0)}; box.addView(status)
            var year:EditText?=null
            if(c.dpdns){ year=EditText(this).apply{hint="截止年份（2024-2099）";setText(prefs.getInt("dpdns_year",2026).toString());inputType=2;setSingleLine(true);textSize=14f;setTextColor(Color.parseColor("#4A2B20"));gravity=Gravity.CENTER}; box.addView(year,LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(5)}); year.setOnFocusChangeListener{_,has->if(!has) setDpdnsYear(c,year!!.text.toString())} }
            val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)}}
            val start=Button(this).apply{text="开始";setTextColor(Color.WHITE);textSize=14f;background=getDrawable(R.drawable.bg_button);setAllCaps(false);setOnClickListener{start(c)}}
            val reset=Button(this).apply{text="重置";setTextColor(Color.parseColor("#8C6A5B"));textSize=14f;background=getDrawable(R.drawable.bg_button_secondary);setAllCaps(false);setOnClickListener{reset(c)}}
            buttons.addView(start,LinearLayout.LayoutParams(0,dp(44),1f).apply{rightMargin=dp(5)}); buttons.addView(reset,LinearLayout.LayoutParams(0,dp(44),1f).apply{leftMargin=dp(5)}); box.addView(buttons)
            root.addView(box); cards[c.id]=CardViews(ds,hs,ms,ss,status,year)
        }
    }

    private fun start(c:Counter){
        val now=System.currentTimeMillis(); val key=c.id+"_end"; val current=prefs.getLong(key,0L)
        if(current<=now){
            if(c.dpdns){val y=cards[c.id]?.year?.text?.toString()?.toIntOrNull()?.coerceIn(2024,2099)?:2026; prefs.edit().putInt("dpdns_year",y).putLong(key,utcJuly28(y)).apply()}
            else prefs.edit().putLong(key,now+c.days*86400000L).apply()
        }
        updateAll()
    }
    private fun reset(c:Counter){
        val key=c.id+"_end"
        if(c.dpdns){val y=cards[c.id]?.year?.text?.toString()?.toIntOrNull()?.coerceIn(2024,2099)?:2026;prefs.edit().putInt("dpdns_year",y).putLong(key,utcJuly28(y)).apply()}
        else prefs.edit().remove(key).apply()
        updateAll()
    }
    private fun setDpdnsYear(c:Counter,s:String){val y=s.toIntOrNull()?.coerceIn(2024,2099)?:2026;prefs.edit().putInt("dpdns_year",y).putLong(c.id+"_end",utcJuly28(y)).apply();updateAll()}
    private fun utcJuly28(year:Int):Long{val cal=Calendar.getInstance(TimeZone.getTimeZone("UTC"));cal.clear();cal.set(year,Calendar.JULY,28,0,0,0);return cal.timeInMillis}
    private fun updateAll(){
        val now=System.currentTimeMillis()
        counters.forEach{c->val v=cards[c.id]?:return@forEach;val end=prefs.getLong(c.id+"_end",0L);if(end<=0L){set(v,0);v.status.text="未开始"}else{val d=end-now;if(d<=0){set(v,0);v.status.text="倒计时已结束！"}else{val sec=d/1000;set(v,sec/86400,(sec%86400)/3600,(sec%3600)/60,sec%60);v.status.text="正在倒计时"}}}
    }
    private fun set(v:CardViews,d:Long,h:Long=0,m:Long=0,s:Long=0){v.days.text="%02d".format(d);v.hours.text="%02d".format(h);v.mins.text="%02d".format(m);v.secs.text="%02d".format(s)}
    private fun dp(x:Int)= (x*resources.displayMetrics.density).toInt()
}

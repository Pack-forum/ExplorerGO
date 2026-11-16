package com.explorer.launcher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.*
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import java.io.File
import java.io.FileOutputStream

class AppItem(val label: String, val pkg: String, val icon: Drawable, val orig: String)

class MainActivity : Activity() {
    companion object { const val HOME = 0; const val APPS = 1; const val SET = 2 }
    private val FONTS = arrayOf("sans-serif-thin", "sans-serif-light", "sans-serif", "sans-serif-condensed", "sans-serif-medium")

    private lateinit var root: FrameLayout
    private lateinit var wall: ImageView
    private lateinit var dim: View
    private lateinit var box: FrameLayout
    private lateinit var gd: GestureDetector
    private lateinit var awh: AppWidgetHost
    private lateinit var awm: AppWidgetManager
    private var lockV: FrameLayout? = null
    private var lockContent: View? = null
    private var startV: FrameLayout? = null
    private var startEt: EditText? = null
    private var startAdapter: AppAdapter? = null
    private var barV: View? = null
    private var barCenter: LinearLayout? = null
    private var pager: Pager? = null
    private var dotsV: LinearLayout? = null
    private var pageViews = ArrayList<FrameLayout>()
    private val pages = arrayOfNulls<View>(3)
    private var cur = HOME
    private var back = HOME
    private var locked = true
    private var apps = listOf<AppItem>()
    private var allApps = listOf<AppItem>()
    private var shown = listOf<AppItem>()
    private var query = ""
    private var moving: String? = null
    private var lastLoad = 0L
    private var pendingLock = false
    private var setScroll: ScrollView? = null
    private var setSec = ""
    private var setQ = ""
    private var homeBmp: Bitmap? = null
    private var lockBmp: Bitmap? = null
    private var blurBmp: Bitmap? = null
    private var wallKey = ""
    private var pickTarget = ""
    private var pendingWid = -1
    private var rcv: BroadcastReceiver? = null
    private var inL = 0; private var inT = 0; private var inR = 0; private var inB = 0
    private var lDownY = 0f
    private var lVt: VelocityTracker? = null
    private val am by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private val CATS = arrayOf(
        arrayOf("nav", "Navigasyon", "\uD83E\uDDED"), arrayOf("music", "Müzik", "\uD83C\uDFB5"),
        arrayOf("phone", "Telefon", "\uD83D\uDCDE"), arrayOf("sms", "Mesajlar", "\uD83D\uDCAC"),
        arrayOf("camera", "Kamera", "\uD83D\uDCF7"), arrayOf("browser", "Tarayıcı", "\uD83C\uDF10"),
        arrayOf("contacts", "Rehber", "\uD83D\uDC64"), arrayOf("radio", "Radyo", "\uD83D\uDCFB"))
    private val SECS = arrayOf(
        arrayOf("Görünüm", "\uD83C\uDFA8", "Görünüm", "Tema, sistem rengi, cam efekti, animasyon"),
        arrayOf("Duvar kağıdı", "\uD83D\uDDBC", "Duvar kağıdı", "Kilit ve ana ekran için ayrı arka plan"),
        arrayOf("Kilit ekranı", "\uD83D\uDD12", "Kilit ekranı", "Saniyeli saat, perde, müzik tuşları"),
        arrayOf("Ana menü", "\uD83E\uDDE9", "Ana menü ve widget", "Sol panel, widget ekleme"),
        arrayOf("Alt çubuk", "\uD83D\uDDA5", "Alt çubuk", "Başlat düğmesi, sabit uygulamalar"),
        arrayOf("Uygulama listesi", "\uD83D\uDCF1", "Uygulama listesi", "Sütun, satır, ikon, sıralama, gizli"),
        arrayOf("Varsayılan uygulamalar", "\u2B50", "Varsayılan uygulamalar", "Navigasyon, müzik, telefon, kamera…"),
        arrayOf("Sistem", "\u2699", "Sistem", "Yön, açılış, sıfırlama"))

    // ---------- yardımcılar ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun tv(t: String, sp: Float, col: Int = P.fg(), bold: Boolean = false): TextView = TextView(this).apply {
        text = t; textSize = sp; setTextColor(col); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun ctrl(sym: String, sz: Int, padH: Int = 18, a: () -> Unit): TextView = tv(sym, sz.toFloat()).apply {
        gravity = Gravity.CENTER; setPadding(dp(padH), dp(8), dp(padH), dp(8)); setOnClickListener { a() }
    }
    private fun round(col: Int, r: Int) = GradientDrawable().apply { setColor(col); cornerRadius = dp(r).toFloat() }
    private fun clockColor() = when (P.i("ccolor", 0)) { 0 -> P.accent(); 1 -> P.fg(); else -> Color.WHITE }
    private fun dfmt() = arrayOf("EEEE, d MMMM", "d MMMM yyyy", "dd.MM.yyyy", "EEE d MMM")[P.i("dfmt", 0).coerceIn(0, 3)]
    private fun radii(tl: Float, tr: Float, br: Float, bl: Float) = floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
    private fun cardBg(): Drawable { val a = P.accent(); return round(Color.argb(60, Color.red(a), Color.green(a), Color.blue(a)), 12) }
    private fun barH() = if (P.b("bar", true)) dp(P.i("barH", 52)) else 0
    private fun glassTint(): Int {
        val a = P.accent(); val base = if (P.dark()) 0xFF0C0F09.toInt() else 0xFFFFFFFF.toInt()
        val c = Gfx.blend(base, a, if (P.dark()) 0.26f else 0.16f)
        val al = if (P.b("blur", true)) P.i("glassA", 55) else maxOf(P.i("glassA", 55), 88)
        return Color.argb(al * 255 / 100, Color.red(c), Color.green(c), Color.blue(c))
    }
    private fun glass(v: View, rad: FloatArray) { v.background = GlassDrawable(v, root, if (P.b("blur", true)) blurBmp else null, glassTint(), rad) }

    // ---------- yaşam döngüsü ----------
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        P.init(this)
        awm = AppWidgetManager.getInstance(this); awh = AppWidgetHost(this, 1024)
        root = FrameLayout(this)
        wall = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        dim = View(this); box = FrameLayout(this)
        root.addView(wall, FrameLayout.LayoutParams(-1, -1))
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))
        root.addView(box, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = 0x66000000
        window.navigationBarColor = 0x99000000.toInt()
        root.setOnApplyWindowInsetsListener { _, w ->
            inL = w.systemWindowInsetLeft; inT = w.systemWindowInsetTop; inR = w.systemWindowInsetRight; inB = w.systemWindowInsetBottom
            applyInsets(); w
        }
        sysUi()
        gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x; val dy = e2.y - e1.y
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > dp(70)) swipe(if (dx < 0) 2 else 3)
                return false
            }
        })
        rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { pendingLock = P.b("lock", true) && P.b("lockOff", true) }
        }
        registerReceiver(rcv, IntentFilter(Intent.ACTION_SCREEN_OFF))
        locked = P.b("lock", true) && P.i("startPage", 0) == 0
        loadApps(); rebuild()
        root.requestApplyInsets()
    }

    override fun onStart() { super.onStart(); try { awh.startListening() } catch (e: Exception) {} }
    override fun onStop() { super.onStop(); try { awh.stopListening() } catch (e: Exception) {} }
    override fun onDestroy() { super.onDestroy(); try { unregisterReceiver(rcv) } catch (e: Exception) {} }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!locked) gd.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (locked) return
        if (startV?.visibility == View.VISIBLE) hideStart() else if (cur != HOME) go(HOME)
    }

    override fun onResume() {
        super.onResume()
        if (pendingLock) { pendingLock = false; if (P.b("lock", true)) lockNow(false) }
    }

    override fun onWindowFocusChanged(h: Boolean) { super.onWindowFocusChanged(h); if (h) sysUi() }

    private fun sysUi() {
        val base = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        window.decorView.systemUiVisibility = if (P.b("full", false))
            base or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else base
    }

    private fun applyInsets() { box.setPadding(inL, inT, inR, inB); lockContent?.setPadding(inL, inT, inR, inB) }

    override fun onBackPressed() {
        when {
            locked -> {}
            moving != null -> { moving = null; filterApps() }
            startV?.visibility == View.VISIBLE -> hideStart()
            cur == SET -> if (setSec != "") { setSec = ""; rebuild() } else go(back)
            cur == APPS -> go(HOME)
        }
    }

    // ---------- duvar kağıdı ----------
    private fun wallFile(n: String) = File(filesDir, "wp_$n.jpg")
    private fun decode(uri: Uri): Bitmap? {
        val dm = resources.displayMetrics; val target = maxOf(dm.widthPixels, dm.heightPixels)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (o.outWidth / (s * 2) >= target) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 }
        return contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, o2) }
    }
    private fun loadWalls() {
        fun pick(n: String): File? { val a = wallFile(n); if (a.exists()) return a; val g = File(filesDir, "wp.jpg"); return if (g.exists()) g else null }
        val fh = pick("home"); val fk = pick("lock")
        val key = "${fh?.lastModified()}|${fk?.lastModified()}|${if (fh == null || fk == null) P.accent() else 0}|${P.b("blur", true)}"
        if (key == wallKey && homeBmp != null) return
        wallKey = key
        fun ld(f: File?): Bitmap? = try { if (f != null) decode(Uri.fromFile(f)) else null } catch (e: Exception) { null }
        var dflt: Bitmap? = null
        fun d(): Bitmap { if (dflt == null) dflt = Gfx.defaultWall(P.accent()); return dflt!! }
        homeBmp = ld(fh) ?: d(); lockBmp = ld(fk) ?: d()
        blurBmp = Gfx.blurSmall(homeBmp!!)
    }
    private fun wallPick(name: String) {
        AlertDialog.Builder(this).setTitle("Duvar kağıdı").setItems(arrayOf("Galeriden seç", "Kaldır")) { _, i ->
            if (i == 0) { pickTarget = name; startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 11) }
            else { (if (name == "") File(filesDir, "wp.jpg") else wallFile(name)).delete(); wallKey = ""; rebuild() }
        }.show()
    }

    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        when (rq) {
            11 -> {
                val u = d?.data
                if (rs == RESULT_OK && u != null) try {
                    val b = decode(u)!!
                    FileOutputStream(if (pickTarget == "") File(filesDir, "wp.jpg") else wallFile(pickTarget)).use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    wallKey = ""; rebuild()
                } catch (e: Exception) { toast("Resim yüklenemedi") }
            }
            21 -> {
                if (rs != RESULT_OK) { try { awh.deleteAppWidgetId(pendingWid) } catch (e: Exception) {}; return }
                val id = d?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWid) ?: pendingWid
                val info = awm.getAppWidgetInfo(id)
                if (info == null) { toast("Widget eklenemedi"); return }
                if (info.configure != null) try { awh.startAppWidgetConfigureActivityForResult(this, id, 0, 22, null) } catch (e: Exception) { addWid(id) }
                else addWid(id)
            }
            22 -> if (rs == RESULT_OK) addWid(pendingWid) else try { awh.deleteAppWidgetId(pendingWid) } catch (e: Exception) {}
        }
    }

    // ---------- widget ----------
    private fun widgetIds() = P.s("widgets", "").split(",").filter { it.isNotEmpty() }.map { it.toInt() }
    private fun addWid(id: Int) { P.put("widgets", (widgetIds() + id).joinToString(",")); rebuild() }
    private fun widgetPick() {
        pendingWid = awh.allocateAppWidgetId()
        startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWid)
            .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList<Bundle>())
            .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Bundle>()), 21)
    }
    private fun removeWidget(id: Int) {
        AlertDialog.Builder(this).setTitle("Widget kaldırılsın mı?")
            .setPositiveButton("Kaldır") { _, _ ->
                try { awh.deleteAppWidgetId(id) } catch (e: Exception) {}
                P.put("widgets", widgetIds().filter { it != id }.joinToString(",")); rebuild()
            }.setNegativeButton("İptal", null).show()
    }

    // ---------- kilit ----------
    private fun showBehind(v: Boolean) { val vis = if (v) View.VISIBLE else View.GONE; box.visibility = vis; wall.visibility = vis; dim.visibility = vis }
    private fun unlock() {
        val l = lockV ?: return; locked = false; showBehind(true); hideStart()
        l.animate().cancel()
        l.animate().translationY(-l.height.toFloat()).setDuration(if (P.b("anim", true)) 280 else 0).setInterpolator(DecelerateInterpolator())
            .withLayer().withEndAction { if (!locked) l.visibility = View.GONE }.start()
    }
    private fun bounceBack() {
        val l = lockV ?: return
        l.animate().translationY(0f).setDuration(if (P.b("anim", true)) 600 else 0).setInterpolator(BounceInterpolator())
            .withLayer().withEndAction { if (locked) showBehind(false) }.start()
    }
    private fun lockNow(anim: Boolean = true) {
        val l = lockV ?: return; locked = true; hideStart(); l.animate().cancel(); l.visibility = View.VISIBLE
        if (anim && P.b("anim", true)) {
            showBehind(true); l.translationY = -l.height.toFloat()
            l.animate().translationY(0f).setDuration(600).setInterpolator(BounceInterpolator()).withLayer().withEndAction { if (locked) showBehind(false) }.start()
        } else { l.translationY = 0f; showBehind(false) }
    }

    // ---------- gezinme ----------
    private fun swipe(d: Int) { // 2 sola, 3 sağa
        if (locked || startV?.visibility == View.VISIBLE) return
        if (cur == HOME && d == 2) go(APPS)
    }

    private fun go(p: Int) {
        if (p == cur) return
        if (p == SET) {
            back = cur
            if (setSec != "") {
                setSec = ""; val nv = buildSettings(); val idx = box.indexOfChild(pages[SET]); box.removeView(pages[SET])
                box.addView(nv, idx, FrameLayout.LayoutParams(-1, -1)); nv.visibility = View.GONE; pages[SET] = nv
            }
        }
        if (p == APPS && System.currentTimeMillis() - lastLoad > 30000) loadApps()
        hideStart()
        val old = pages[cur]!!; val nw = pages[p]!!; cur = p
        barV?.visibility = if (p == SET) View.GONE else View.VISIBLE
        old.animate().cancel(); nw.animate().cancel()
        if (!P.b("anim", true)) { for (i in pages.indices) pages[i]?.apply { visibility = if (i == p) View.VISIBLE else View.GONE; alpha = 1f }; return }
        val d = if (P.i("animSpeed", 1) == 0) 110L else 180L
        nw.alpha = 0f; nw.visibility = View.VISIBLE
        nw.animate().alpha(1f).setDuration(d).withLayer().start()
        old.animate().alpha(0f).setDuration(d).withLayer().withEndAction { if (pages[cur] !== old) old.visibility = View.GONE; old.alpha = 1f }.start()
    }

    // ---------- uygulama başlatma ----------
    private fun launch(pkg: String): Boolean {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true
    }
    private fun openNav() {
        val pkg = P.s("navPkg", "")
        if (pkg.isNotEmpty() && launch(pkg)) return
        for (c in arrayOf("com.google.android.apps.maps", "ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "com.waze",
            "com.here.app.maps", "app.organicmaps", "net.osmand", "com.sygic.aura")) if (launch(c)) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) {}
        toast("Ayarlar > Varsayılan uygulamalar"); go(SET)
    }
    private fun openMusic() {
        val pkg = P.s("musicPkg", "")
        if (pkg.isNotEmpty() && launch(pkg)) return
        try { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { go(APPS) }
    }
    private fun catKey(k: String) = when (k) { "nav" -> "navPkg"; "music" -> "musicPkg"; else -> "cat_$k" }
    private fun launchCat(k: String) {
        hideStart()
        if (k == "nav") { openNav(); return }
        if (k == "music") { openMusic(); return }
        val pkg = P.s(catKey(k), "")
        if (pkg.isNotEmpty() && launch(pkg)) return
        try {
            val i: Intent? = when (k) {
                "phone" -> Intent(Intent.ACTION_DIAL)
                "sms" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
                "camera" -> Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                "browser" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                "contacts" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
                else -> null
            }
            if (i == null) { toast("Ayarlardan uygulama seç"); return }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i)
        } catch (e: Exception) { toast("Ayarlardan uygulama seç") }
    }
    private fun key(code: Int) { am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
    private fun sys(a: String) { try { startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast("Açılamadı") } }
    private fun sys0(i: Intent) { try { startActivity(i) } catch (e: Exception) {} }

    // ---------- uygulama listesi (ikonlar küçültülüp önbelleğe alınır) ----------
    private fun hiddenSet() = P.s("hidden", "").split(",").filter { it.isNotEmpty() }.toSet()
    private fun loadApps() {
        lastLoad = System.currentTimeMillis()
        Thread {
            val pm = packageManager
            val sz = dp(maxOf(P.i("isize", 48), P.i("barIcon", 28), 40))
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val hidden = hiddenSet()
            val all = pm.queryIntentActivities(i, 0).filter { it.activityInfo.packageName != packageName }.map {
                val pk = it.activityInfo.packageName; val o = it.loadLabel(pm).toString()
                val b = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
                val d = it.loadIcon(pm); d.setBounds(0, 0, sz, sz); d.draw(Canvas(b))
                AppItem(P.s("name_$pk", "").ifEmpty { o }, pk, BitmapDrawable(resources, b), o)
            }.sortedBy { it.label.lowercase() }
            val vis = all.filter { it.pkg !in hidden }
            val order = P.s("order", "").split(",").filter { it.isNotEmpty() }
            val sorted = if (P.i("sort", 0) == 0) vis.sortedBy { val x = order.indexOf(it.pkg); if (x < 0) Int.MAX_VALUE else x } else vis
            runOnUiThread { allApps = all; apps = sorted; filterApps(); fillBar() }
        }.start()
    }
    private fun filterApps() { shown = apps.filter { it.label.contains(query, true) }; fillApps() }

    inner class AppAdapter(var data: List<AppItem>, val sz: Int, val ts: Float) : BaseAdapter() {
        override fun getCount() = data.size
        override fun getItem(p: Int) = data[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val ctx = this@MainActivity
            val ll = (cv as? LinearLayout) ?: LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(2), dp(4), dp(2), dp(4))
                addView(ImageView(ctx)); addView(TextView(ctx).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER })
            }
            val iv = ll.getChildAt(0) as ImageView; iv.layoutParams = LinearLayout.LayoutParams(dp(sz), dp(sz)); iv.setImageDrawable(data[p].icon)
            val t = ll.getChildAt(1) as TextView; t.text = data[p].label; t.textSize = ts; t.setTextColor(P.fg())
            return ll
        }
    }

    private fun doMove(t: AppItem) {
        val m = moving ?: return
        val l = apps.map { it.pkg }.toMutableList(); val f = l.indexOf(m); val to = l.indexOf(t.pkg)
        if (f >= 0 && to >= 0 && f != to) {
            l.removeAt(f); l.add(to, m); P.put("order", l.joinToString(","))
            apps = l.mapNotNull { pk -> apps.firstOrNull { it.pkg == pk } }
        }
        moving = null; filterApps()
    }
    private fun appMenu(a: AppItem) {
        val pinned = P.s("pins", "").split(",").contains(a.pkg)
        val o = arrayOf("\u2194  Yerini değiştir", "\u270F  Yeniden adlandır", "\uD83D\uDE48  Gizle", "\uD83D\uDCCC  Başa taşı",
            if (pinned) "\u2796  Alt çubuktan kaldır" else "\u2795  Alt çubuğa sabitle", "\u2139  Uygulama bilgisi", "\uD83D\uDDD1  Kaldır")
        AlertDialog.Builder(this).setTitle(a.label).setItems(o) { _, i ->
            when (i) {
                0 -> if (query.isNotEmpty()) toast("Önce aramayı temizle") else if (P.i("sort", 0) != 0) toast("Ayarlar > Uygulama listesi > Özel sıra")
                     else { moving = a.pkg; filterApps(); toast("Taşımak istediğin yere dokun (iptal: geri)") }
                1 -> {
                    val et = EditText(this).apply { setText(a.label); setSingleLine() }
                    AlertDialog.Builder(this).setTitle("Yeni ad (boş = orijinal)").setView(et)
                        .setPositiveButton("Kaydet") { _, _ -> P.put("name_" + a.pkg, et.text.toString().trim()); loadApps() }
                        .setNegativeButton("İptal", null).show()
                }
                2 -> { P.put("hidden", (hiddenSet() + a.pkg).joinToString(",")); loadApps(); toast("Gizlendi (Ayarlar'dan geri alınır)") }
                3 -> { val l = listOf(a.pkg) + apps.map { it.pkg }.filter { it != a.pkg }; P.put("order", l.joinToString(",")); loadApps() }
                4 -> {
                    val l = P.s("pins", "").split(",").filter { it.isNotEmpty() }
                    P.put("pins", (if (pinned) l - a.pkg else l + a.pkg).joinToString(",")); fillBar()
                }
                5 -> sys0(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)))
                6 -> sys0(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)))
            }
        }.show()
    }

    // ---------- sayfaları kur ----------
    private fun rebuild() {
        val sy = setScroll?.scrollY ?: 0
        loadWalls()
        wall.setImageBitmap(homeBmp)
        root.setBackgroundColor(P.bg())
        dim.setBackgroundColor(if (P.dark()) Color.BLACK else Color.WHITE); dim.alpha = P.i("dim", 25) / 100f
        requestedOrientation = when (P.i("orient", 0)) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (P.b("keepOn", true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sysUi()
        box.removeAllViews(); query = ""; moving = null
        pages[HOME] = buildHome(); pages[APPS] = buildApps(); pages[SET] = buildSettings()
        for (p in pages) { p!!.visibility = View.GONE; box.addView(p, FrameLayout.LayoutParams(-1, -1)) }
        pages[cur]!!.visibility = View.VISIBLE
        barV = buildBar(); barV?.let { box.addView(it, FrameLayout.LayoutParams(-1, barH(), Gravity.BOTTOM)); it.visibility = if (cur == SET) View.GONE else View.VISIBLE }
        startV = buildStart(); box.addView(startV, FrameLayout.LayoutParams(-1, -1))
        lockV?.let { root.removeView(it) }
        lockV = buildLock(); root.addView(lockV, FrameLayout.LayoutParams(-1, -1))
        if (locked) { lockV!!.visibility = View.VISIBLE; showBehind(false) } else { lockV!!.visibility = View.GONE; showBehind(true) }
        applyInsets(); filterApps(); fillBar()
        if (cur == SET) setScroll?.post { setScroll?.scrollTo(0, sy) }
    }

    private fun buildHome(): View {
        val fl = FrameLayout(this)
        val right = P.i("pside", 0) == 1
        if (P.b("panel", true)) {
            val r = dp(P.i("corner", 16)).toFloat()
            val panel = FrameLayout(this); glass(panel, if (right) radii(r, 0f, 0f, r) else radii(0f, r, r, 0f))
            val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; setPadding(dp(10), dp(10), dp(10), dp(10)); clipToPadding = false }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            sv.addView(col); panel.addView(sv, FrameLayout.LayoutParams(-1, -1))
            fun item(v: View) = col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            if (P.b("wNav", true)) item(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = cardBg(); setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(tv("\uD83E\uDDED", 24f))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(tv("Navigasyon", 15f, P.fg(), true)); addView(tv("Dokun ve başlat", 11f).apply { alpha = 0.65f })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
                setOnClickListener { launchCat("nav") }
            })
            if (P.b("wMusic", true)) item(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = cardBg(); setPadding(dp(10), dp(8), dp(10), dp(6))
                addView(tv("\uD83C\uDFB5  Müzik", 15f, P.fg(), true).apply { setOnClickListener { launchCat("music") } })
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER
                    addView(ctrl("\u23EE", 22, 12) { key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) })
                    addView(ctrl("\u23EF", 24, 12) { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })
                    addView(ctrl("\u23ED", 22, 12) { key(KeyEvent.KEYCODE_MEDIA_NEXT) })
                })
            })
            if (P.b("wQuick", true)) item(LinearLayout(this).apply {
                background = cardBg(); gravity = Gravity.CENTER; setPadding(dp(2), dp(4), dp(2), dp(4))
                for (k in arrayOf("phone", "sms", "camera", "browser")) { val ct = CATS.first { it[0] == k }; addView(ctrl(ct[2], 20, 8) { launchCat(k) }) }
            })
            for (id in widgetIds()) {
                try {
                    val info = awm.getAppWidgetInfo(id) ?: continue
                    val hv = awh.createView(this, id, info)
                    hv.setOnLongClickListener { removeWidget(id); true }
                    col.addView(hv, LinearLayout.LayoutParams(-1, info.minHeight.coerceIn(dp(80), dp(220))).apply { bottomMargin = dp(8) })
                } catch (e: Exception) {}
            }
            item(tv("+  Widget ekle", 13f).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(12).toFloat(); setStroke(dp(1), 0x88FFFFFF.toInt(), dp(5).toFloat(), dp(4).toFloat()) }
                setOnClickListener { widgetPick() }
            })
            fl.addView(panel, FrameLayout.LayoutParams(dp(P.i("panelW", 190)), -1, if (right) Gravity.END else Gravity.START).apply { bottomMargin = barH() })
        }
        fl.addView(tv("\u203A", 34f).apply { alpha = 0.7f; setPadding(dp(10), dp(20), dp(10), dp(20)); setOnClickListener { go(APPS) } },
            FrameLayout.LayoutParams(-2, -2, (if (right) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL))
        return fl
    }

    private fun buildBar(): View? {
        if (!P.b("bar", true)) return null
        val isz = P.i("barIcon", 28)
        val bar = FrameLayout(this); glass(bar, radii(0f, 0f, 0f, 0f))
        bar.addView(StartLogo(this).apply { accent = P.accent(); setOnClickListener { toggleStart() } },
            FrameLayout.LayoutParams(dp(isz + 16), dp(isz + 16), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(10) })
        val center = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        barCenter = center
        val left = P.i("barAlign", 0) == 1
        bar.addView(center, FrameLayout.LayoutParams(-2, -1, if (left) Gravity.START else Gravity.CENTER_HORIZONTAL).apply { if (left) leftMargin = dp(isz + 34) })
        val tray = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        if (P.b("tLock", true)) tray.addView(ctrl("\uD83D\uDD12", 18, 10) { lockNow() })
        if (P.b("tVol", true)) tray.addView(ctrl("\uD83D\uDD0A", 18, 10) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI) })
        bar.addView(tray, FrameLayout.LayoutParams(-2, -1, Gravity.END).apply { rightMargin = dp(6) })
        return bar
    }

    private fun fillBar() {
        val c = barCenter ?: return; c.removeAllViews()
        val isz = P.i("barIcon", 28); val pins = P.s("pins", "").split(",").filter { it.isNotEmpty() }
        fun add(v: View) = c.addView(v, LinearLayout.LayoutParams(dp(isz + 18), -1))
        if (pins.isEmpty()) {
            for (ct in CATS) {
                if (ct[0] == "radio" && P.s("cat_radio", "").isEmpty()) continue
                add(tv(ct[2], isz * 0.7f).apply { gravity = Gravity.CENTER; setOnClickListener { launchCat(ct[0]) } })
            }
        } else for (pk in pins) {
            val a = allApps.firstOrNull { it.pkg == pk } ?: continue
            add(ImageView(this).apply { setImageDrawable(a.icon); setPadding(dp(9), dp(9), dp(9), dp(9)); setOnClickListener { hideStart(); launch(pk) } })
        }
    }

    private fun toggleStart() {
        val s = startV ?: return
        if (s.visibility == View.VISIBLE) { hideStart(); return }
        startEt?.setText(""); startAdapter?.data = apps.take(8); startAdapter?.notifyDataSetChanged()
        s.alpha = 0f; s.visibility = View.VISIBLE; s.animate().alpha(1f).setDuration(120).withLayer().start()
    }
    private fun hideStart() { startV?.visibility = View.GONE }

    private fun buildStart(): FrameLayout {
        val layer = FrameLayout(this).apply { visibility = View.GONE; setOnClickListener { hideStart() } }
        val pop = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(10)); isClickable = true }
        glass(pop, FloatArray(8) { dp(P.i("corner", 16)).toFloat() })
        val ad = AppAdapter(apps.take(8), 40, 11f); startAdapter = ad
        val gv = GridView(this).apply {
            numColumns = 4; adapter = ad; selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setOnItemClickListener { _, _, p, _ -> hideStart(); launch(ad.data[p].pkg) }
        }
        val et = EditText(this).apply {
            hint = "Uygulama ara…"; setSingleLine(); textSize = 14f; setTextColor(P.fg()); setHintTextColor(Color.GRAY)
            background = round(0x22FFFFFF, 10); setPadding(dp(12), dp(8), dp(12), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { val q = s.toString(); ad.data = apps.filter { it.label.contains(q, true) }.take(8); ad.notifyDataSetChanged() }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        startEt = et
        pop.addView(et, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        pop.addView(gv, LinearLayout.LayoutParams(-1, dp(170)))
        pop.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(tv("Tüm uygulamalar  \u203A", 13f, P.accent(), true).apply { setPadding(0, dp(8), dp(8), 0); setOnClickListener { hideStart(); go(APPS) } }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(tv("\u2699  Ayarlar", 13f, P.fg(), true).apply { setPadding(dp(8), dp(8), 0, 0); setOnClickListener { hideStart(); go(SET) } })
        })
        val w = minOf(dp(360), resources.displayMetrics.widthPixels - dp(24))
        layer.addView(pop, FrameLayout.LayoutParams(w, -2, Gravity.BOTTOM or (if (P.i("barAlign", 0) == 0) Gravity.CENTER_HORIZONTAL else Gravity.START))
            .apply { bottomMargin = barH() + dp(8); if (P.i("barAlign", 0) == 1) leftMargin = dp(8) })
        return layer
    }

    private fun buildApps(): View {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), barH() + dp(4)); setBackgroundColor((P.bg() and 0x00FFFFFF) or 0x99000000.toInt()) }
        val et = EditText(this).apply {
            hint = "Uygulama ara…"; setSingleLine(); setTextColor(P.fg()); setHintTextColor(Color.GRAY)
            background = round(0x33000000, 14); setPadding(dp(16), dp(8), dp(16), dp(8)); textSize = 15f
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s.toString(); filterApps() }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        val pg = Pager(this); pager = pg
        val dots = LinearLayout(this).apply { gravity = Gravity.CENTER }; dotsV = dots
        pg.onSized = { fillApps() }
        pg.onPage = { p -> ensurePage(p - 1); ensurePage(p); ensurePage(p + 1); setDots() }
        pg.onEdgeRight = { go(HOME) }
        ll.addView(et, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        ll.addView(pg, LinearLayout.LayoutParams(-1, 0, 1f))
        ll.addView(dots, LinearLayout.LayoutParams(-1, dp(18)))
        return ll
    }

    private fun appCell(a: AppItem): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; alpha = if (a.pkg == moving) 0.35f else 1f
        val s = dp(P.i("isize", 48))
        addView(ImageView(this@MainActivity).apply { setImageDrawable(a.icon) }, LinearLayout.LayoutParams(s, s))
        if (P.b("labels", true)) addView(TextView(this@MainActivity).apply {
            text = a.label; textSize = P.i("lsz", 11).toFloat(); setTextColor(P.fg()); maxLines = 1
            ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(dp(2), dp(2), dp(2), 0)
        })
        setOnClickListener { if (moving != null) doMove(a) else { hideStart(); launch(a.pkg) } }
        setOnLongClickListener { appMenu(a); true }
    }

    /** Sayfalar boş oluşturulur; yalnızca görünen sayfa ve komşuları doldurulur (hafif). */
    private fun fillApps() {
        val pg = pager ?: return
        if (pg.width <= 0 || pg.height <= 0) return
        val per = P.i("cols", 7) * P.i("rows", 3)
        val n = maxOf(1, (shown.size + per - 1) / per)
        pageViews = ArrayList()
        val list = ArrayList<View>()
        for (i in 0 until n) { val f = FrameLayout(this); pageViews.add(f); list.add(f) }
        pg.setPages(list)
    }
    private fun ensurePage(i: Int) {
        if (i < 0 || i >= pageViews.size) return
        val f = pageViews[i]; if (f.childCount > 0) return
        val cols = P.i("cols", 7); val rows = P.i("rows", 3); val per = cols * rows
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until cols) {
                val idx = i * per + r * cols + c
                row.addView(if (idx < shown.size) appCell(shown[idx]) else View(this), LinearLayout.LayoutParams(0, -1, 1f))
            }
            page.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        f.addView(page, FrameLayout.LayoutParams(-1, -1))
    }
    private fun setDots() {
        val d = dotsV ?: return; val pg = pager ?: return; d.removeAllViews()
        if (pg.count <= 1) return
        for (k in 0 until pg.count) d.addView(View(this).apply { background = round(if (k == pg.page) P.accent() else 0x66FFFFFF, 8) },
            LinearLayout.LayoutParams(dp(7), dp(7)).apply { setMargins(dp(3), 0, dp(3), 0) })
    }

    private fun buildLock(): FrameLayout {
        val fl = FrameLayout(this)
        fl.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(lockBmp) }, FrameLayout.LayoutParams(-1, -1))
        fl.addView(View(this).apply { setBackgroundColor(0x44000000) }, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = when (P.i("lpos", 1)) { 0 -> Gravity.TOP or Gravity.CENTER_HORIZONTAL; 2 -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; else -> Gravity.CENTER }
            setPadding(inL, inT, inR, inB)
        }
        lockContent = content
        val size = P.i("lsize", 96).toFloat(); val f = if (P.b("h24", true)) "HH:mm" else "h:mm"
        val tf = Typeface.create(FONTS[P.i("cfont", 1).coerceIn(0, 4)], Typeface.NORMAL)
        content.addView(tv("EXPLORER GO", 12f, P.accent(), true).apply { letterSpacing = 0.3f; setPadding(0, 0, 0, dp(6)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL }
        row.addView(TextClock(this).apply {
            format12Hour = f; format24Hour = f; textSize = size; setTextColor(clockColor()); typeface = tf; includeFontPadding = false
            if (P.b("cshadow", true)) setShadowLayer(10f, 0f, 3f, 0x99000000.toInt())
        })
        if (P.b("sec", true)) row.addView(TextClock(this).apply {
            format12Hour = ":ss"; format24Hour = ":ss"; textSize = size * 0.4f; setTextColor(clockColor()); typeface = tf; includeFontPadding = false
            if (P.b("cshadow", true)) setShadowLayer(10f, 0f, 3f, 0x99000000.toInt())
        })
        content.addView(row)
        if (P.b("ldate", true)) content.addView(TextClock(this).apply {
            format12Hour = dfmt(); format24Hour = dfmt(); textSize = 18f; setTextColor(Color.WHITE); setPadding(0, dp(2), 0, 0)
        })
        if (P.b("lmusic", true)) content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0)
            addView(ctrl("\u23EE", 28) { key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) })
            addView(ctrl("\u23EF", 30) { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })
            addView(ctrl("\u23ED", 28) { key(KeyEvent.KEYCODE_MEDIA_NEXT) })
        })
        fl.addView(content, FrameLayout.LayoutParams(-1, -1))
        fl.addView(tv("\u2303  Açmak için yukarı kaydır", 14f, Color.WHITE).apply { alpha = 0.7f },
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = inB + dp(16) })
        fl.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lDownY = e.rawY; lVt?.recycle(); lVt = VelocityTracker.obtain(); lVt!!.addMovement(e); v.animate().cancel(); showBehind(true) }
                MotionEvent.ACTION_MOVE -> { lVt?.addMovement(e); v.translationY = minOf(0f, e.rawY - lDownY) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lVt?.addMovement(e); lVt?.computeCurrentVelocity(1000)
                    val vy = lVt?.yVelocity ?: 0f; val dy = e.rawY - lDownY
                    lVt?.recycle(); lVt = null
                    if (dy < -v.height * 0.22f || vy < -800f) unlock() else bounceBack()
                }
            }
            true
        }
        return fl
    }

    // ---------- ayarlar ----------
    private fun seekL(onCh: (Int) -> Unit, onStop: (Int) -> Unit = {}) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { onCh(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) { onStop(s!!.progress) }
    }
    private fun tint(s: SeekBar) { s.progressTintList = ColorStateList.valueOf(P.accent()); s.thumbTintList = ColorStateList.valueOf(P.accent()) }

    private fun buildSettings(): View {
        val sv = ScrollView(this); setScroll = sv
        sv.setBackgroundColor((P.bg() and 0x00FFFFFF) or 0xF2000000.toInt())
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(40)) }
        sv.addView(ll)
        val items = ArrayList<Triple<View, String, String>>()
        var curSec = ""
        setQ = ""
        fun add(v: View, t: String = "") { items.add(Triple(v, t, curSec)); ll.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }) }
        fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(P.surf(), 14); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        fun head(t: String) { curSec = t }
        fun sw(t: String, k: String, d: Boolean) {
            val c = card()
            c.addView(Switch(this).apply { text = t; textSize = 16f; setTextColor(P.fg()); isChecked = P.b(k, d); setOnCheckedChangeListener { _, v -> P.put(k, v); rebuild() } })
            add(c, t)
        }
        fun sk(t: String, k: String, mn: Int, mx: Int, d: Int, u: String = "", reload: Boolean = false) {
            val c = card(); val lab = tv("$t: ${P.i(k, d)}$u", 16f); c.addView(lab)
            c.addView(SeekBar(this).apply {
                max = mx - mn; progress = (P.i(k, d) - mn).coerceIn(0, mx - mn); tint(this)
                setOnSeekBarChangeListener(seekL({ p -> lab.text = "$t: ${p + mn}$u" }, { p -> P.put(k, p + mn); rebuild(); if (reload) loadApps() }))
            }); add(c, t)
        }
        fun ch(t: String, k: String, o: Array<String>, d: Int) {
            val c = card().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            c.addView(tv(t, 16f), LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(tv(o[P.i(k, d).coerceIn(0, o.size - 1)], 16f, P.accent(), true))
            c.setOnClickListener { AlertDialog.Builder(this).setTitle(t).setItems(o) { _, i -> P.put(k, i); rebuild() }.show() }
            add(c, t)
        }
        fun bt(t: String, a: () -> Unit) { val c = card(); c.addView(tv(t, 16f)); c.setOnClickListener { a() }; add(c, t) }
        fun appPick(t: String, k: String) {
            val cur0 = allApps.firstOrNull { it.pkg == P.s(k, "-") }?.label ?: "Otomatik"
            bt("$t: $cur0") {
                if (allApps.isEmpty()) toast("Liste yükleniyor…") else
                    AlertDialog.Builder(this).setTitle(t).setItems(allApps.map { it.label }.toTypedArray()) { _, i -> P.put(k, allApps[i].pkg); rebuild() }
                        .setNeutralButton("Otomatik / sıfırla") { _, _ -> P.put(k, ""); rebuild() }.show()
            }
        }

        var vis: () -> Unit = {}
        val secInfo = SECS.firstOrNull { it[0] == setSec }
        ll.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ctrl("\u2190", 24) { if (setSec != "") { setSec = ""; rebuild() } else go(back) })
            addView(tv(secInfo?.get(2) ?: "Explorer GO Ayarları", 22f, P.fg(), true))
        })
        if (setSec == "") ll.addView(EditText(this).apply {
            hint = "\uD83D\uDD0E  Ayarlarda ara…"; setSingleLine(); setTextColor(P.fg()); setHintTextColor(Color.GRAY)
            background = round(P.surf(), 14); setPadding(dp(16), dp(10), dp(16), dp(10)); textSize = 16f
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) { setQ = e.toString().trim(); vis() }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(10) })
        val catBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ll.addView(catBox)
        for (sc in SECS) catBox.addView(card().apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(tv(sc[1], 26f))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(tv(sc[2], 17f, P.fg(), true)); addView(tv(sc[3], 13f).apply { alpha = 0.6f })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
            addView(tv("\u203A", 26f, P.accent()))
            setOnClickListener { setSec = sc[0]; rebuild() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        head("Görünüm")
        ch("Tema", "theme", arrayOf("Koyu", "Açık", "Otomatik (gece/gündüz)"), 0)
        ch("Sistem rengi", "accent", P.accentNames, 0)
        sw("Cam efekti (blur)", "blur", true)
        sk("Panel saydamlığı", "glassA", 20, 95, 55, "%")
        sk("Köşe yuvarlaklığı", "corner", 0, 28, 16, "dp")
        sk("Gece modu başlangıcı (otomatik)", "nightFrom", 15, 23, 19, ":00")
        sk("Gece modu bitişi (otomatik)", "nightTo", 3, 10, 7, ":00")
        sw("Animasyonlar", "anim", true)
        ch("Animasyon hızı", "animSpeed", arrayOf("Hızlı", "Normal"), 1)

        head("Duvar kağıdı")
        bt("\uD83D\uDDBC  Tüm ekranlar (genel)") { wallPick("") }
        bt("Kilit ekranı: " + (if (wallFile("lock").exists()) "özel" else "genel")) { wallPick("lock") }
        bt("Ana ekran ve uygulamalar: " + (if (wallFile("home").exists()) "özel" else "genel")) { wallPick("home") }
        bt("Tüm duvar kağıtlarını kaldır") { filesDir.listFiles()?.filter { it.name.startsWith("wp") }?.forEach { it.delete() }; wallKey = ""; rebuild() }
        sk("Karartma", "dim", 0, 90, 25, "%")

        head("Kilit ekranı")
        sw("Kilit ekranı açık", "lock", true)
        sw("Ekran kapanınca kilitle", "lockOff", true)
        sw("Saniye göster", "sec", true)
        sw("24 saat formatı", "h24", true)
        sw("Tarih göster", "ldate", true)
        ch("Tarih biçimi", "dfmt", arrayOf("Pazartesi, 5 Mayıs", "5 Mayıs 2025", "05.05.2025", "Pzt 5 May"), 0)
        sk("Saat boyutu", "lsize", 40, 200, 96, "sp")
        ch("Yazı tipi", "cfont", arrayOf("İnce", "Hafif", "Normal", "Dar", "Orta"), 1)
        ch("Saat rengi", "ccolor", arrayOf("Sistem rengi", "Tema rengi", "Beyaz"), 0)
        ch("Dikey konum", "lpos", arrayOf("Üst", "Orta", "Alt"), 1)
        sw("Gölge", "cshadow", true)
        sw("Müzik tuşları", "lmusic", true)

        head("Ana menü")
        sw("Sol panel", "panel", true)
        ch("Panel yeri", "pside", arrayOf("Sol", "Sağ"), 0)
        sk("Panel genişliği", "panelW", 120, 300, 190, "dp")
        sw("Navigasyon kartı", "wNav", true)
        sw("Müzik kartı", "wMusic", true)
        sw("Hızlı uygulama satırı", "wQuick", true)
        bt("\u2795  Widget ekle") { widgetPick() }
        bt("Tüm widget'ları kaldır") { for (id in widgetIds()) try { awh.deleteAppWidgetId(id) } catch (e: Exception) {}; P.put("widgets", ""); rebuild() }

        head("Alt çubuk")
        sw("Alt çubuk açık", "bar", true)
        sk("Yükseklik", "barH", 40, 80, 52, "dp")
        sk("İkon boyutu", "barIcon", 18, 40, 28, "dp", true)
        ch("Hizalama", "barAlign", arrayOf("Orta", "Sol"), 0)
        sw("Kilit düğmesi", "tLock", true)
        sw("Ses düğmesi", "tVol", true)
        bt("Sabitlenen uygulamaları sıfırla") { P.put("pins", ""); rebuild() }

        head("Uygulama listesi")
        sk("Sütun sayısı", "cols", 4, 10, 7)
        sk("Satır sayısı", "rows", 2, 5, 3)
        sk("İkon boyutu", "isize", 32, 80, 48, "dp", true)
        sk("İsim yazı boyutu", "lsz", 9, 16, 11, "sp")
        sw("İsimleri göster", "labels", true)
        ch("Sıralama", "sort", arrayOf("Özel sıra (uzun bas > Yerini değiştir)", "A-Z"), 0)
        bt("Özel sırayı sıfırla") { P.put("order", ""); loadApps(); toast("Sıfırlandı") }
        bt("\uD83D\uDE48  Gizli uygulamalar (${hiddenSet().size})") {
            val hs = allApps.filter { it.pkg in hiddenSet() }
            if (hs.isEmpty()) toast("Gizli uygulama yok") else {
                val chk = BooleanArray(hs.size) { true }
                AlertDialog.Builder(this).setTitle("İşareti kaldırdığın geri gelir")
                    .setMultiChoiceItems(hs.map { it.label }.toTypedArray(), chk) { _, i, v -> chk[i] = v }
                    .setPositiveButton("Tamam") { _, _ ->
                        val keep = hs.filterIndexed { i, _ -> chk[i] }.map { it.pkg }
                        P.put("hidden", (hiddenSet() - hs.map { it.pkg }.toSet() + keep).joinToString(",")); loadApps(); rebuild()
                    }.setNegativeButton("İptal", null).show()
            }
        }

        head("Varsayılan uygulamalar")
        for (ct in CATS) appPick(ct[2] + "  " + ct[1], catKey(ct[0]))

        head("Sistem")
        ch("Açılış ekranı", "startPage", arrayOf("Kilit ekranı", "Ana menü"), 0)
        sw("Ekranı açık tut", "keepOn", true)
        sw("Tam ekran (sistem tuşlarını gizle)", "full", false)
        ch("Ekran yönü", "orient", arrayOf("Otomatik (yön uygulaman belirler)", "Yatay", "Dikey"), 0)
        bt("Varsayılan ana ekranı seç") { sys(Settings.ACTION_HOME_SETTINGS) }
        bt("Android ayarlarını aç") { sys(Settings.ACTION_SETTINGS) }
        bt("Tüm ayarları sıfırla") {
            AlertDialog.Builder(this).setTitle("Tüm ayarlar sıfırlansın mı?")
                .setPositiveButton("Sıfırla") { _, _ -> P.sp.edit().clear().apply(); setSec = ""; wallKey = ""; rebuild() }
                .setNegativeButton("İptal", null).show()
        }
        bt("Explorer GO 1.0  •  Honor 7S (Android 8.1) için optimize") {}

        vis = {
            val q = setQ; val searching = q.isNotEmpty()
            catBox.visibility = if (setSec == "" && !searching) View.VISIBLE else View.GONE
            for ((v, t, sec) in items)
                v.visibility = if (searching) (if (t.contains(q, true)) View.VISIBLE else View.GONE) else (if (sec == setSec) View.VISIBLE else View.GONE)
        }
        vis()
        return sv
    }
}

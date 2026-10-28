package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Personal-fork downloads: fixed HTTPS origin, pinned hashes and package identity, OS-confirmed install. */
final class PersonalPackages {
    static final String ORIGIN = "https://nainv.github.io/HomeTV";
    static final String ZIKRA_PACKAGE = "com.zikra.tv";
    static final String MOON_PACKAGE = "com.flextv.livestorex";
    static final String SELF_PACKAGE = "com.gathin.tvhome";
    static final long MAX_APK = 64L * 1024 * 1024;
    static final class Target {
        final String label, pkg, url, sha;
        final long version;
        Target(String label, String pkg, String url, String sha, long version) {
            this.label=label; this.pkg=pkg; this.url=url; this.sha=sha; this.version=version;
        }
    }
    interface Progress { void bytes(long received, long total); }
    static Target moon() {
        return new Target("Moon TV", MOON_PACKAGE,
                "https://github.com/Nainv/HomeTV/releases/download/Moon/MOON.PLAYER.4K.-.v3.5.apk",
                "57a5685e775fc6d5da629162b875b82e8e9c5e2dcd1e77c9b87cfeafae4f47fd", 39);
    }
    static Target zikra() throws Exception {
        // Resolve the current asset digest from the owner's release, so replacement
        // APKs at the stable URL never get blocked by an obsolete hard-coded hash.
        HttpURLConnection connection = connect(
                "https://api.github.com/repos/Nainv/ZApp/releases/tags/v1.0.5");
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > 262144) throw new IOException("بيانات الإصدار كبيرة جدًا");
                out.write(buffer, 0, count);
            }
            org.json.JSONArray assets = new JSONObject(out.toString(StandardCharsets.UTF_8.name()))
                    .getJSONArray("assets");
            String expected = "https://github.com/Nainv/ZApp/releases/download/v1.0.5/Zikratv.apk";
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (!"Zikratv.apk".equals(asset.getString("name"))) continue;
                String hash = asset.optString("digest", "");
                if (!expected.equals(asset.getString("browser_download_url"))
                        || !hash.matches("sha256:[a-fA-F0-9]{64}"))
                    throw new IOException("بيانات ملف ذكرى غير صالحة");
                return new Target("ذِكرى", ZIKRA_PACKAGE, expected, hash.substring(7), 0);
            }
            throw new IOException("ملف ذكرى غير موجود بالإصدار");
        } finally { connection.disconnect(); }
    }
    static Target latest() throws Exception {
        boolean fireOs6 = Build.VERSION.SDK_INT < 26;
        HttpURLConnection c=connect(ORIGIN+"/release.json?t="+System.currentTimeMillis());
        try (InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[2048]; int n;
            while((n=in.read(b))!=-1) { if(out.size()+n>16384)throw new IOException("بيانات التحديث أكبر من المتوقع");out.write(b,0,n); }
            JSONObject j=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            String sha=j.getString(fireOs6?"fireOs6Sha256":"sha256");
            String url=j.getString(fireOs6?"fireOs6Url":"url");
            long version=j.getLong(fireOs6?"fireOs6VersionCode":"versionCode");
            String versionName=j.getString(fireOs6?"fireOs6VersionName":"versionName");
            if(!UpdateRules.valid(ORIGIN,url,sha,version))throw new IOException("بيانات التحديث غير صالحة");
            return new Target("تحديث الواجهة "+versionName,SELF_PACKAGE,url,sha,version);
        } finally { c.disconnect(); }
    }
    static HttpURLConnection connect(String value) throws Exception {
        URL url = new URL(value);
        for (int redirects = 0; redirects < 6; redirects++) {
            String host = url.getHost();
            if (!"https".equals(url.getProtocol()) || !(
                    "nainv.github.io".equals(host)
                    || "api.github.com".equals(host)
                    || "github.com".equals(host)
                    || "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host)))
                throw new IOException("مصدر التنزيل غير مسموح");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000); connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            int status = connection.getResponseCode();
            if (status == 200) return connection;
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("رابط التحويل مفقود");
                url = new URL(url, location);
            } else {
                connection.disconnect();
                throw new IOException("تعذّر التنزيل (" + status + ")");
            }
        }
        throw new IOException("تحويلات التنزيل كثيرة جدًا");
    }
    static File file(Context c,Target t){return new File(new File(c.getCacheDir(),"installers"),t.pkg+"-"+t.version+".apk");}
    static File download(Context context,Target target,Progress progress)throws Exception {
        Context c=context.getApplicationContext();File dest=file(c,target);
        if(dest.isFile()){try{validate(c,dest,target);return dest;}catch(Exception e){if(!dest.delete())throw new IOException("تعذّر استبدال التنزيل السابق");}}
        File dir=dest.getParentFile();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("لا توجد مساحة للتنزيل");
        File temp=new File(dir,dest.getName()+".part");HttpURLConnection conn=connect(target.url);
        try { long total=conn.getContentLengthLong();if(total>MAX_APK)throw new IOException("حجم الملف أكبر من المسموح");
            try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(temp)){
                byte[] buffer=new byte[32768];int n;long count=0,last=0;
                while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("تم إيقاف التنزيل");count+=n;if(count>MAX_APK)throw new IOException("الملف كبير جداً");out.write(buffer,0,n);
                    if(count-last>131072){progress.bytes(count,total);last=count;}}
                if(count==0||(total>=0&&count!=total))throw new IOException("التنزيل غير مكتمل");
            }
            validate(c,temp,target);if(!temp.renameTo(dest))throw new IOException("تعذّر حفظ الملف");return dest;
        } finally {conn.disconnect();if(temp.exists())temp.delete();}
    }
    @SuppressWarnings("deprecation")
    static void validate(Context c,File f,Target t)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!hex.toString().equalsIgnoreCase(t.sha))throw new IOException("الملف لا يطابق النسخة المعتمدة. أعد المحاولة لاحقاً.");
        PackageManager pm=c.getPackageManager();PackageInfo p=pm.getPackageArchiveInfo(f.getPath(),PackageManager.GET_SIGNATURES);
        if(p==null||!t.pkg.equals(p.packageName)||(t.version > 0 && p.versionCode!=t.version))throw new IOException("حزمة أو إصدار التطبيق غير صحيح");
        if(t.pkg.equals(c.getPackageName())){
            PackageInfo current=pm.getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNATURES);
            if(current.signatures==null||p.signatures==null||!java.util.Arrays.equals(current.signatures,p.signatures))throw new IOException("توقيع التحديث لا يطابق التطبيق الحالي");
        }
    }
    static long installedVersion(Context c)throws Exception {return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode;}
    static void install(android.app.Activity activity,File file){
        Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".installfiles",file);
        Intent i=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(i);
    }
    static boolean openZikra(android.app.Activity a){return openPackage(a,ZIKRA_PACKAGE);}
    static boolean openMoon(android.app.Activity a){return openPackage(a,MOON_PACKAGE);}
    private static boolean openPackage(android.app.Activity a,String pkg){
        Intent i=a.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if(i==null)i=a.getPackageManager().getLaunchIntentForPackage(pkg);
        if(i==null)return false;try{a.startActivity(i);return true;}catch(Exception e){return false;}
    }
}

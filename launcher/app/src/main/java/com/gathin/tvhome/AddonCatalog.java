package com.gathin.tvhome;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Only add-ons bundled in this source catalog may be downloaded. */
final class AddonCatalog {
    static final String REPOSITORY = "Nainv/HomeTV", TAG = "v3.7.9";
    static final class Entry {
        final String id, pkg, ar, en, noteAr, noteEn;
        final int minSdk;
        Entry(JSONObject item) throws Exception {
            id=item.getString("id"); pkg=item.getString("packageName"); minSdk=item.getInt("minSdk");
            ar=item.getString("ar"); en=item.getString("en");
            noteAr=item.optString("noteAr"); noteEn=item.optString("noteEn");
            if(!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !pkg.matches("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+")
                    || minSdk<26) throw new IllegalArgumentException("Invalid add-on entry");
        }
        String label(Context c) { return arabic(c)?ar:en; }
        String note(Context c) { return arabic(c)?noteAr:noteEn; }
        String assetName() { return "home-addon-"+id+".apk"; }
    }
    private static boolean arabic(Context c) {
        return "ar".equals(c.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }
    static List<Entry> load(Context context) throws Exception {
        try(InputStream in=context.getAssets().open("addons/catalog.json");
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096]; int count;
            while((count=in.read(buffer))!=-1) {
                if(out.size()+count>65536)throw new IllegalArgumentException("Catalog too large");
                out.write(buffer,0,count);
            }
            JSONArray values=new JSONArray(out.toString(StandardCharsets.UTF_8.name()));
            List<Entry> entries=new ArrayList<>(); java.util.HashSet<String> ids=new java.util.HashSet<>();
            for(int i=0;i<values.length();i++) {
                Entry entry=new Entry(values.getJSONObject(i));
                if(!ids.add(entry.id))throw new IllegalArgumentException("Duplicate add-on");
                entries.add(entry);
            }
            return entries;
        }
    }
    static Entry find(Context context,String id) throws Exception {
        for(Entry entry:load(context))if(entry.id.equals(id))return entry;
        throw new IllegalArgumentException("Unknown add-on");
    }
}

package com.gathin.tvhome;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.graphics.Rect;
import android.text.method.TransformationMethod;
import android.widget.TextClock;
/** Normalize the final text even on firmware that ignores locale numbering extensions. */
public final class WesternTextClock extends TextClock {
 public WesternTextClock(Context context){this(context,null);}
 public WesternTextClock(Context context,AttributeSet attrs){this(context,attrs,0);}
 public WesternTextClock(Context context,AttributeSet attrs,int style){super(context,attrs,style);setTransformationMethod(new TransformationMethod(){
  public CharSequence getTransformation(CharSequence value,View view){return value==null?null:WesternDigits.text(value);}
  public void onFocusChanged(View view,CharSequence value,boolean focused,int direction,Rect previous){}
 });}
}

package com.gathin.tvhome;
/** Retain Arabic words and punctuation while displaying all numbers with 0-9. */
final class WesternDigits {
 private WesternDigits(){}
 static String text(CharSequence value){String source=value.toString();StringBuilder out=null;
  for(int i=0;i<source.length();i++){char c=source.charAt(i);int digit=c>='\u0660'&&c<='\u0669'?c-'\u0660':c>='\u06f0'&&c<='\u06f9'?c-'\u06f0':-1;
   if(digit>=0){if(out==null)out=new StringBuilder(source);out.setCharAt(i,(char)('0'+digit));}}
  return out==null?source:out.toString();}
}

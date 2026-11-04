# ez-vcard references Jackson, FreeMarker and jsoup for jCard and hCard support, which are excluded from the build.
-dontwarn com.fasterxml.jackson.**
-dontwarn freemarker.**
-dontwarn org.jsoup.**
# ez-vcard instantiates property classes reflectively when copying cards and reads its version from a bundled resource.
-keep class ezvcard.** { *; }

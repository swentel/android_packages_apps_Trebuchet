/*
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2017 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.icons;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;

import com.android.launcher3.IconCache;
import com.android.launcher3.IconProvider;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AdaptiveIconDrawableCompat;
import com.android.launcher3.compat.FixedScaleDrawableCompat;
import com.android.launcher3.graphics.IconNormalizer;
import com.android.launcher3.graphics.IconShapeOverride;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.DrawableHack;
import com.android.launcher3.util.ResourceHack;

import org.xmlpull.v1.XmlPullParser;

public class CustomIconsProvider extends IconProvider {
    private static final boolean flag_bypass_roundicons = false;

    private Context mContext;
    private IconsHandler mHandler;
    private boolean existsAdaptive = false;

    public CustomIconsProvider(Context context) {
        super();
        mContext = context;
        mHandler = IconCache.getIconsHandler(context);

        try {
            Class.forName( "android.graphics.drawable.AdaptiveIconDrawable");
            existsAdaptive=true;
        } catch( ClassNotFoundException e ) {}
    }

    public static Drawable defaultIcon = null;
    public static Drawable.ConstantState defaultIconConstantState = null;
    public static Bitmap defaultIconBitmap = null;
    public static boolean isIconDefault(Drawable drawableA) {
        Drawable.ConstantState stateA = drawableA.getConstantState();
        if (defaultIcon==null) defaultIcon = LauncherAppState.getInstanceNoCreate().getContext().getPackageManager().getDefaultActivityIcon();
        if (defaultIconConstantState==null) defaultIconConstantState = defaultIcon.getConstantState();
        return (stateA != null && defaultIconConstantState != null && stateA.equals(defaultIconConstantState))
                || getBitmap(drawableA).sameAs((defaultIconBitmap!=null)?defaultIconBitmap:(defaultIconBitmap=getBitmap(defaultIcon)));
    }

    public static Bitmap getBitmap(Drawable drawable) {
        Bitmap result;
        if (drawable instanceof BitmapDrawable) {
            result = ((BitmapDrawable) drawable).getBitmap();
        } else {
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            // Some drawables have no intrinsic width - e.g. solid colours.
            if (width <= 0) {
                width = 1;
            }
            if (height <= 0) {
                height = 1;
            }

            result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        return result;
    }

    private int inflateIconId(Resources resourcesForApplication, String activityName, boolean roundIcon, boolean[] roundIconFlag) {
        int resId = 0;
        int resIdGlobal = 0;
        roundIconFlag[0] = false;
        boolean roundIconFlagActivity = false;
        boolean roundIconFlagApplication = false;
        String activityString;
        AssetManager assets = resourcesForApplication.getAssets();
        try (XmlResourceParser parseXml = assets.openXmlResourceParser("AndroidManifest.xml")) {
            String attribute = null;
            int eventType;
            while ((eventType = parseXml.nextToken()) != XmlPullParser.END_DOCUMENT) {
                boolean activityFound = false;
                String tagName = parseXml.getName();
                if (activityName!=null && !activityName.isEmpty() && eventType == XmlPullParser.START_TAG && tagName.equals("activity"))
                    for (int i = 0; i < parseXml.getAttributeCount(); i++) {
                        attribute = parseXml.getAttributeName(i);
                        if (attribute.equals("name")) {
                            activityString = parseXml.getAttributeValue(i);
                            if (activityString!=null && !activityString.isEmpty() && activityName.endsWith(activityString)) activityFound = true;
                            else activityFound = false;
                        }
                        else if ((!roundIcon || resId == 0) && attribute.equals("icon")) {
                            resId = Integer.parseInt(parseXml.getAttributeValue(i).substring(1));
                            roundIconFlagActivity=false;
                        }
                        else if (roundIcon && attribute.equals("roundIcon")) {
                            resId = Integer.parseInt(parseXml.getAttributeValue(i).substring(1));
                            roundIconFlagActivity=true;
                        }
                    }
                else if (eventType == XmlPullParser.START_TAG && tagName.equals("application"))
                    for (int i = 0; i < parseXml.getAttributeCount(); i++) {
                        attribute = parseXml.getAttributeName(i);
                        if ((!roundIcon || resIdGlobal == 0) && attribute.equals("icon")) {
                            resIdGlobal = Integer.parseInt(parseXml.getAttributeValue(i).substring(1));
                            roundIconFlagApplication=false;
                        }
                        else if (roundIcon && attribute.equals("roundIcon")) {
                            resIdGlobal = Integer.parseInt(parseXml.getAttributeValue(i).substring(1));
                            roundIconFlagApplication=true;
                            break;
                        }
                    }
                if (resId!=0 && activityFound) break;
                else if (resId!=0 && !activityFound) resId=0;
            }
        } catch (Exception ex) {
            android.util.Log.e("CustomIconsProvider", "Error parsing xml", ex);
        }
        roundIconFlag[0] = roundIconFlagActivity;
        if (resId==0) {resId=resIdGlobal; roundIconFlag[0]=roundIconFlagApplication;}
        if (resId==0) roundIconFlag[0]=false;
        return resId;
    }

    private int inflateIconId(Resources resourcesForApplication, String activityName, boolean roundIcon) {
        return inflateIconId(resourcesForApplication, activityName, roundIcon, new boolean[1]);
    }

    public Drawable getLegacyIcon(LauncherActivityInfo info, int iconDpi) {
        String packageName = info.getComponentName().getPackageName();
        String activityName = info.getName();

        PackageManager mPackageManager = null;
        Resources resourcesForApplication = null;
        try {
            mPackageManager = mContext.getPackageManager();
            resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);

            int resId = inflateIconId(resourcesForApplication, activityName, false);
            if (resId!=0) {
                resourcesForApplication = ResourceHack.setResSdk(resourcesForApplication, 25);
                return resourcesForApplication.getDrawableForDensity(resId, iconDpi);
            }
            else {
                resId = mContext.getPackageManager().getActivityInfo(info.getComponentName(), PackageManager.GET_SHARED_LIBRARY_FILES).icon;
                if (resId == 0) resId = info.getApplicationInfo().icon;
                if (resId == 0) return null;
                resourcesForApplication = ResourceHack.setResSdk(resourcesForApplication, 25);
                Drawable legacyIcon = resourcesForApplication.getDrawableForDensity(resId, iconDpi, null);
                //This is necessary for some reason
                if (legacyIcon == null) throw new Exception();
                return legacyIcon;
            }
        } catch (Exception ex) {
            if (resourcesForApplication!=null) try{
                resourcesForApplication=ResourceHack.setResSdk(resourcesForApplication, android.os.Build.VERSION.SDK_INT);
                resourcesForApplication.flushLayoutCache();
            }catch (Exception e){}
            Log.e("CustomIconsProvider", "Failure retrieving legacy icon for activity: "+info.getName());
        }
        return null;
    }

    public Drawable getRoundIconBackport(String packageName, String activityName, int iconDpi) {
        boolean isBypassingBuiltin = Utilities.isBuiltinAdaptiveIconBypassed(mContext);
        boolean isBypassingOEMTheme = Utilities.isBuiltinThemeBypassed(mContext);
        boolean[] roundIconFlag = new boolean[] {false};
        Drawable legacyIcon = null;
        PackageManager mPackageManager = mContext.getPackageManager();
        Resources resourcesForApplication = null;
        try {resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);}
        catch (PackageManager.NameNotFoundException e) {return null;}

        int resId = inflateIconId(resourcesForApplication, activityName, true, roundIconFlag);

        if (resId!=0) try {
            if (!Utilities.ATLEAST_OREO) resourcesForApplication = ResourceHack.setResSdk(resourcesForApplication, 26);
            try {
                if (isBypassingBuiltin) throw new Resources.NotFoundException("");
                legacyIcon = resourcesForApplication.getDrawableForDensity(resId, iconDpi);
            } catch (Resources.NotFoundException e) {
                Object drawableInflater = DrawableHack.getDrawableInflater(resourcesForApplication);
                XmlPullParser parser = resourcesForApplication.getXml(resId);
                legacyIcon = DrawableHack.inflateFromXml(drawableInflater, parser);
            }
        } catch (Exception e) {}
        if (isBypassingBuiltin && (flag_bypass_roundicons || isBypassingOEMTheme) && !(legacyIcon instanceof AdaptiveIconDrawableCompat)) try {
            legacyIcon = resourcesForApplication.getDrawableForDensity(resId, iconDpi);
        } catch (Exception e) {}

        if (resId!=0 && legacyIcon==null && !Utilities.ATLEAST_OREO) try{resourcesForApplication=ResourceHack.setResSdk(resourcesForApplication, android.os.Build.VERSION.SDK_INT);} catch (Exception e){}
        if (Utilities.ATLEAST_OREO && isBypassingBuiltin && !(legacyIcon instanceof AdaptiveIconDrawableCompat) && !roundIconFlag[0]) legacyIcon=null;
        if (!Utilities.ATLEAST_OREO && !isBypassingOEMTheme && !(legacyIcon instanceof  AdaptiveIconDrawableCompat) &&
                ((!roundIconFlag[0]&&flag_bypass_roundicons)||!flag_bypass_roundicons) && (!existsAdaptive || !(legacyIcon instanceof AdaptiveIconDrawable))) legacyIcon=null;
        return legacyIcon;
    }

    public static Drawable getDeepShortcutIconBackport(int resId, Resources resourcesForApplication) {
        Drawable icon = null;
        Drawable legacyIcon = null;
        Context mContext = LauncherAppState.getInstanceNoCreate().getContext();
        XmlPullParser parser;
        if (resId!=0 && resourcesForApplication != null) try {
            resourcesForApplication = ResourceHack.setResSdk(resourcesForApplication, 25);
            try {legacyIcon = resourcesForApplication.getDrawableForDensity(resId, LauncherAppState.getInstanceNoCreate().getInvariantDeviceProfile().fillResIconDpi);}
            catch (Exception e){}
            if (legacyIcon != null && legacyIcon instanceof AdaptiveIconDrawableCompat) {ResourceHack.setResSdk(resourcesForApplication, 26);return legacyIcon;}
            else if (legacyIcon != null && Utilities.isAdaptiveIconDisabled(mContext)) return legacyIcon;
            ResourceHack.setResSdk(resourcesForApplication, 26);
            //icon = resourcesForApplication.getDrawableForDensity(resId, LauncherAppState.getInstanceNoCreate().getInvariantDeviceProfile().fillResIconDpi);
            try{
                Object drawableInflater = DrawableHack.getDrawableInflater(resourcesForApplication);
                resourcesForApplication.flushLayoutCache();
                parser = resourcesForApplication.getXml(resId);
                icon = DrawableHack.inflateFromXml(drawableInflater, parser);
            } catch (Exception e){return legacyIcon;}
            if (icon == null) {ResourceHack.setResSdk(resourcesForApplication, 25);return legacyIcon;}
            if (icon instanceof AdaptiveIconDrawableCompat && ((AdaptiveIconDrawableCompat)icon).missingLayer != null) try {
                if (legacyIcon == null) return null;
                if (legacyIcon instanceof LayerDrawable) ((LayerDrawable)legacyIcon).getDrawable(0).mutate().setAlpha(0);

                AdaptiveIconDrawableCompat.ChildDrawable layer = ((AdaptiveIconDrawableCompat)icon).missingLayer;
                layer.mDrawable = new FixedScaleDrawableCompat();
                float scale = IconNormalizer.getInstance(mContext).getScale(legacyIcon, null, ((AdaptiveIconDrawableCompat)icon).getIconMask(), new boolean[1]);
                ((FixedScaleDrawableCompat)layer.mDrawable).setDrawable(legacyIcon);
                ((FixedScaleDrawableCompat)layer.mDrawable).setScale(scale*1.8F);
                layer.mDrawable.setCallback((AdaptiveIconDrawableCompat)icon);
                ((AdaptiveIconDrawableCompat)icon).mLayerState.mChildrenChangingConfigurations |= layer.mDrawable.getChangingConfigurations();
                ((AdaptiveIconDrawableCompat)icon).addLayer(((AdaptiveIconDrawableCompat)icon).missingLayerIndex, layer);
                ((AdaptiveIconDrawableCompat)icon).missingLayer = null;
            }
            catch (Exception e) {return null;}
        } catch (Exception e) {Log.e("CustomIconsProvider","Error creating shortcut icon", e);}
        if (resId!=0 && legacyIcon==null && icon==null) try{resourcesForApplication=ResourceHack.setResSdk(resourcesForApplication, 25);} catch (Exception e){}
        return icon!=null?icon:legacyIcon;
    }

    public static Drawable getDeepShortcutIconBypass(int resId, Resources resourcesForApplication) {
        Drawable legacyIcon = null;

        if (resId!=0) try {
                Object drawableInflater = DrawableHack.getDrawableInflater(resourcesForApplication);
                XmlPullParser parser = resourcesForApplication.getXml(resId);
                legacyIcon = DrawableHack.inflateFromXml(drawableInflater, parser);
        } catch (Exception e) {}
        if (!(legacyIcon instanceof AdaptiveIconDrawableCompat)) legacyIcon = null;
        return legacyIcon;
    }

    public Drawable wrapToAdaptiveIconBackport(Drawable drawable) {
        if ((Utilities.ATLEAST_OREO && !Utilities.isBuiltinAdaptiveIconBypassed(mContext)) || !(Utilities.isAdaptiveIconForced(mContext))) {
            return drawable;
        }

        float scale;
        boolean[] outShape = new boolean[1];
        AdaptiveIconDrawableCompat iconWrapper = new AdaptiveIconDrawableCompat(new ColorDrawable(mContext.getResources().getColor(R.color.legacy_icon_background)), new FixedScaleDrawableCompat(), Utilities.ATLEAST_MARSHMALLOW);
        try {
            if (!(drawable instanceof AdaptiveIconDrawableCompat) && (!Utilities.ATLEAST_OREO || !(drawable instanceof AdaptiveIconDrawable))) {
                scale = IconNormalizer.getInstance(mContext).getScale(drawable, null, iconWrapper.getIconMask(), outShape);
                FixedScaleDrawableCompat fsd = ((FixedScaleDrawableCompat) iconWrapper.getForeground());
                fsd.setDrawable(drawable);
                fsd.setScale(scale);
                return (Drawable) iconWrapper;
            }
        } catch (Exception e) {
            return drawable;
        }
        return drawable;
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        boolean isBuiltinThemeBypassed = Utilities.isBuiltinThemeBypassed(mContext);
        Drawable portedIcon = null;
        Drawable adaptiveBypassIcon = null;

        if (Utilities.ATLEAST_OREO && IconShapeOverride.isSupported(mContext) && Utilities.isAdaptiveIconDisabled(mContext))
            portedIcon = getLegacyIcon(info, iconDpi);
        if (((Utilities.ATLEAST_OREO && !IconShapeOverride.isSupported(mContext)) || (!Utilities.ATLEAST_OREO)) && !Utilities.isAdaptiveIconDisabled(mContext))
            portedIcon = getRoundIconBackport(info.getComponentName().getPackageName(), info.getName(), iconDpi);

        if (Utilities.isUsingIconPack(mContext)) {
            final Bitmap bm = mHandler.getThemedDrawableIconForPackage(info.getComponentName());
            if (bm != null && !bm.sameAs(Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), bm.getConfig())))
                return wrapToAdaptiveIconBackport(new BitmapDrawable(mContext.getResources(), bm));
        }

        if (portedIcon!=null && (isBuiltinThemeBypassed || !Utilities.ATLEAST_OREO)) return wrapToAdaptiveIconBackport(portedIcon);
        if (Utilities.ATLEAST_OREO && Utilities.isBuiltinAdaptiveIconBypassed(mContext))
            adaptiveBypassIcon = getRoundIconBackport(info.getComponentName().getPackageName(), info.getName(), iconDpi);
        if (adaptiveBypassIcon!=null) return wrapToAdaptiveIconBackport(adaptiveBypassIcon);
        if (isBuiltinThemeBypassed) return wrapToAdaptiveIconBackport(info.getIcon(iconDpi));

        Drawable icon = null;
        try {
            icon = mContext.getPackageManager().getActivityIcon(info.getComponentName());
            if (icon!=null && (portedIcon==null || !isIconDefault(icon))) return wrapToAdaptiveIconBackport(icon);
        }
        catch (Exception e) {}
        icon = mContext.getPackageManager().getApplicationIcon(info.getApplicationInfo());
        if (icon!=null && (portedIcon==null || !isIconDefault(icon))) return wrapToAdaptiveIconBackport(icon);
        return wrapToAdaptiveIconBackport(portedIcon!=null?portedIcon:info.getIcon(iconDpi));
    }
}

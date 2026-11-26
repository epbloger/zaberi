package ru.epbloger.zaberi

/** All dimensions are the remaining safe area, after system bars and IME. */
internal data class AdaptiveViewportPolicy(val compact:Boolean,val inlineClose:Boolean,val horizontalPadding:Int,val verticalPadding:Int,val maxContentWidth:Int)
internal fun adaptiveViewport(widthDp:Float,heightDp:Float,fontScale:Float):AdaptiveViewportPolicy {
    require(widthDp.isFinite() && heightDp.isFinite() && fontScale.isFinite())
    require(widthDp>=0f && heightDp>=0f && fontScale>0f)
    val compact=heightDp<360f || widthDp<340f || fontScale>=1.5f
    val inlineClose=heightDp<240f && widthDp>=200f
    return AdaptiveViewportPolicy(compact,inlineClose,if(widthDp<340f)8 else 16,if(heightDp<240f)0 else if(compact)4 else 8,720)
}

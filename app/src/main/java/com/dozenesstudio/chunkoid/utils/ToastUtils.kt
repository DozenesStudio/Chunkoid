package com.dozenesstudio.chunkoid.utils

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.dozenesstudio.chunkoid.R

object ToastUtils {
    private var currentPopup: PopupWindow? = null

    fun show(context: Context, message: String, isError: Boolean = false) {
        currentPopup?.dismiss()

        val toastView = LayoutInflater.from(context).inflate(R.layout.custom_toast, null)
        toastView.findViewById<TextView>(R.id.custom_toast_text).text = message
        val iconView = toastView.findViewById<ImageView>(R.id.toast_icon)
        
        if (isError) {
            toastView.setBackgroundResource(R.drawable.custom_toast_error_background)
            iconView.setImageResource(android.R.drawable.ic_dialog_alert)
        } else {
            toastView.setBackgroundResource(R.drawable.custom_toast_background)
            iconView.setImageResource(R.drawable.info)
        }

        val popupWindow = PopupWindow(
            toastView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        )

        toastView.measure(
            android.view.View.MeasureSpec.UNSPECIFIED,
            android.view.View.MeasureSpec.UNSPECIFIED
        )

        val yOffset = 300
        popupWindow.showAtLocation(
            toastView.rootView,
            Gravity.BOTTOM or Gravity.END,
            24,
            yOffset
        )

        currentPopup = popupWindow

        val slideInAnim = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.slide_in_right)
        toastView.startAnimation(slideInAnim)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val slideOutAnim = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.slide_out_right)
            slideOutAnim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    popupWindow.dismiss()
                    if (currentPopup == popupWindow) {
                        currentPopup = null
                    }
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
            toastView.startAnimation(slideOutAnim)
        }, 3000)
    }
}

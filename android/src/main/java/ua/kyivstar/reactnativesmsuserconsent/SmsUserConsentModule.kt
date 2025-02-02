package ua.kyivstar.reactnativesmsuserconsent

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import android.util.Log

class SmsUserConsentModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val reactContext: ReactApplicationContext? = reactContext
  private var promise: Promise? = null
  private var receiver: SmsRetrieveBroadcastReceiver? = null
  private val E_OTP_ERROR = "E_OTP_ERROR"
  private val RECEIVED_OTP_PROPERTY = "receivedOtpMessage"
  val SMS_CONSENT_REQUEST = 1244
  private val TAG = "SmsUserConsentModule"

  override fun initialize() {
    super.initialize()
    reactContext?.addActivityEventListener(mActivityEventListener)
  }

  override fun getName(): String {
    return "SmsUserConsent"
  }

  private val googleApiAvailability by lazy {
    GoogleApiAvailability.getInstance()
  }

  private val googleConnectionStatus by lazy {
    try {
      return@lazy googleApiAvailability.isGooglePlayServicesAvailable(reactContext)
    } catch (e: java.lang.Exception) {
      Log.d(TAG, "googleApiAvailability.error.catch: " + e.message)
      return@lazy null
    }
  }

  private fun isGooglePlayServicesAvailable() = googleConnectionStatus == ConnectionResult.SUCCESS

  @ReactMethod
  fun listenOTP(promise: Promise) {
    if (this.promise != null) {
      promise.reject(E_OTP_ERROR, Error("Reject previous request"))
    }
    this.promise = promise
    if (reactContext?.currentActivity != null && isGooglePlayServicesAvailable()) {
      val task: Task<Void> = SmsRetriever.getClient(reactContext.currentActivity!!).startSmsUserConsent(null)
      task.addOnSuccessListener(object : OnSuccessListener<Void?> {
        override fun onSuccess(aVoid: Void?) {
          // successfully started an SMS Retriever for one SMS message
          registerReceiver()
        }
      })
      task.addOnFailureListener(object : OnFailureListener {
        override fun onFailure(e: Exception) {
          promise.reject(E_OTP_ERROR, e)
        }
      })
    }
  }

  @ReactMethod
  fun removeOTPListener() {
    unregisterReceiver()
  }

  private fun registerReceiver() {
    if (reactContext?.currentActivity != null && receiver == null) {
        // Receiver yalnızca bir kez kaydedilmeli
        SmsRetrieveBroadcastReceiver.setActivity(reactContext.currentActivity)
        receiver = SmsRetrieveBroadcastReceiver()

        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)

        // Playstore Device and network abuse policy: Intent Redirection hatası için receiver register edilirken bu permission eklenmeli
        val permission = "com.google.android.gms.auth.api.phone.permission.SEND"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reactContext.currentActivity?.registerReceiver(receiver, intentFilter, permission, null, Context.RECEIVER_NOT_EXPORTED)
        } else {
            reactContext.currentActivity?.registerReceiver(receiver, intentFilter, permission, null)
        }
    }
  }

  private fun unregisterReceiver() {
    try {
      // Receiver zaten varsa ve kaydedilmişse, sadece o zaman unregister edilecek
      receiver?.let {
        reactContext?.currentActivity?.unregisterReceiver(it)
        receiver = null
      }
    } catch (e: IllegalArgumentException) {
      // Hata alırsan, receiver zaten kayıtlı değil demektir, uyarı logu yaz
      Log.w(TAG, "Receiver not registered, skipping unregister")
    }
  }

  private val mActivityEventListener: ActivityEventListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, intent: Intent?) {
      when (requestCode) {
        SMS_CONSENT_REQUEST -> {
          unregisterReceiver()  // İşlem sonrasında receiver'ı kaldır
          if (resultCode == RESULT_OK) {
            // Get SMS message content
            val message = intent?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            val map = Arguments.createMap()
            map.putString(RECEIVED_OTP_PROPERTY, message)
            promise?.resolve(map)
          } else {
            promise?.reject(E_OTP_ERROR, Error("Result code: $resultCode"))
          }
          promise = null
        }
      }
    }
  }
}

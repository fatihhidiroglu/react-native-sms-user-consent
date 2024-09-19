package ua.kyivstar.reactnativesmsuserconsent

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver
import android.content.Context;
import android.content.Intent;
import android.util.Log

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import java.lang.ref.WeakReference


class SmsRetrieveBroadcastReceiver: BroadcastReceiver() {


  companion object {
    private var currentActivityRef: WeakReference<Activity>? = null

    fun setActivity(activity: Activity?) {
      currentActivityRef = if (activity != null) WeakReference(activity) else null
    }
  }

  val SMS_CONSENT_REQUEST = 1244

  private var activity: Activity? = currentActivityRef?.get()

  override fun onReceive(context: Context?, intent: Intent) {
    if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.action)) {
      val extras = intent.extras!!
      val smsRetrieverStatus: Status = extras[SmsRetriever.EXTRA_STATUS] as Status
      val statusCode: Int = smsRetrieverStatus.getStatusCode()
      when (statusCode) {
        CommonStatusCodes.SUCCESS ->                     // Get consent intent
        {
          val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
          val componentName = consentIntent?.resolveActivity(context!!.packageManager)
          /* Playstore Device and network abuse policy: Intent Redirection, hatası için aşağıdaki ekstra kontrolleride ekledim, google dökümanından aldım
          hatayı düzelten şey muhtemelen buraya eklenen kod değil "SmsUserConsentModule.kt" içinde receiver'ı register ederken eklenen ekstra permission
          daha sonra buradaki herşey eski haline çevirilip tekrar google play'e gönderilebilir bu sayede çözümün eklenen permission olduğu netleşmiş olur*/
          if (componentName?.packageName == "com.google.android.gms" &&
            componentName.className == "com.google.android.gms.auth.api.phone.ui.UserConsentPromptActivity") {
            activity!!.startActivityForResult(consentIntent, SMS_CONSENT_REQUEST)
          } else {
            Log.e("SmsRetrieveBroadcast", "Untrusted component: ${componentName?.className}")
          }
        }
        CommonStatusCodes.TIMEOUT -> {
        }
      }
    }
  }
}

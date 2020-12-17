package com.mrmobo.whatsappclone

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Message
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.resources.CancelableFontCallback
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.android.synthetic.main.activity_otp.*
import java.util.concurrent.TimeUnit

const val PHONE_NUMBER = "phoneNumber"
class OtpActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    var phoneNumber:String? = null
    var mVerificationId:String? = null
    var mResendToken:PhoneAuthProvider.ForceResendingToken? = null
    private lateinit var progressDialog: ProgressDialog
    private var mCounterDown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)
        initviews()
        startVerify()
        //showTimer(60000)
    }

    private fun startVerify() {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
            phoneNumber!!, // Phone number to verify
            60, // Timeout duration
            TimeUnit.SECONDS, // Unit of timeout
            this, // Activity (for callback binding)
            callbacks) // OnVerificationStateChangedCallbacks
        showTimer(60000)
        progressDialog = createProgressDialog("Sending a verification code", false)
        progressDialog.show()
    }

    private fun showTimer(milliSecInFuture: Long) {
        resendBtn.isEnabled = false
        mCounterDown = object:CountDownTimer(milliSecInFuture, 1000){
            override fun onFinish() {
                resendBtn.isEnabled = true
                counterTv.isVisible = false
            }

            override fun onTick(millisUntilFinished: Long) {
                counterTv.isVisible = true
                counterTv.text = getString(R.string.seconds_remaining, millisUntilFinished/1000)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(mCounterDown != null){
            mCounterDown!!.cancel()
        }
    }

    private fun initviews(){
        phoneNumber = intent.getStringExtra(PHONE_NUMBER)
        verifyTv.text = getString(R.string.verify_number, phoneNumber)
        setSpannableString()

        verificationBtn.setOnClickListener(this)
        resendBtn.setOnClickListener(this)

        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {


                if (::progressDialog.isInitialized){
                    progressDialog.dismiss()
                }
                val smsCode :String? = credential.smsCode
                if(!smsCode.isNullOrBlank())
                    sentcodeEt.setText(smsCode)

                signInWithPhoneAuthCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                if (::progressDialog.isInitialized){
                    progressDialog.dismiss()
                }
                if (e is FirebaseAuthInvalidCredentialsException) {

                } else if (e is FirebaseTooManyRequestsException) {

                }


                notifyUserAndRetry("Your Phone Number might be wrong or connection error. Retry again!")


            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {

                progressDialog.dismiss()
                counterTv.isVisible = false

                mVerificationId = verificationId
                mResendToken = token


            }
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        val  mAuth = FirebaseAuth.getInstance()
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener {
                if (it.isSuccessful){

                    if (::progressDialog.isInitialized) {
                        progressDialog.dismiss()
                    }

                    startActivity(Intent(this, SignUpActivity::class.java))
                    finish()
                } else {
                    if (::progressDialog.isInitialized) {
                        progressDialog.dismiss()
                    }

                    notifyUserAndRetry("Your Phone Number verification failed. Try Again!")
                }
            }
    }

    private fun notifyUserAndRetry(message: String) {
        MaterialAlertDialogBuilder(this).apply {
            setMessage(message)
            setPositiveButton("Ok") { _, _ ->
                showLoginActivity()
            }

            setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

            setCancelable(false)
            create()
            show()
        }
    }


    private fun setSpannableString() {
        val span = SpannableString(getString(R.string.waiting_text, phoneNumber))
        val clickableSpan = object  :ClickableSpan(){
            override fun onClick(widget: View) {
                //send back
                showLoginActivity()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                ds.color = ds.linkColor
            }
        }
        span.setSpan(clickableSpan, span.length - 13 , span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        waitingTv.movementMethod = LinkMovementMethod.getInstance()
        waitingTv.text = span
    }

    private fun showLoginActivity() {
        startActivity(Intent(this, OtpActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }



    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onClick(v: View?) {
        when(v){
            verificationBtn -> {

                val code = sentcodeEt.text.toString()
                if (code.isNotEmpty() && !mVerificationId.isNullOrEmpty()){
                    progressDialog = createProgressDialog("Please wait......", false)
                    progressDialog.show()
                    val credential = PhoneAuthProvider.getCredential(mVerificationId!!, code.toString())
                    signInWithPhoneAuthCredential(credential)
                }
            }
            resendBtn -> {

                val code = sentcodeEt.text.toString()
                if (mResendToken != null){
                    showTimer(60000)
                    progressDialog = createProgressDialog("Sending a verification code", false)
                    progressDialog.show()
                    PhoneAuthProvider.getInstance().verifyPhoneNumber(
                        phoneNumber!!,
                        60,
                        TimeUnit.SECONDS,
                        this,
                        callbacks,
                        mResendToken
                    )
                }
            }
        }
    }
}

fun Context.createProgressDialog(message: String, isCancelable: Boolean): ProgressDialog{
    return ProgressDialog(this).apply {
        setCancelable(false)
        setMessage(message)
        setCanceledOnTouchOutside(false)

    }
}
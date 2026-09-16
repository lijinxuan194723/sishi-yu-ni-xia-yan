package cn.sishiyuni.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keys never enter Room, DataStore, backup, log messages, or bundled resources. Call on IO. */
class SecretVault(context:Context){
 private val storage=context.getSharedPreferences("encrypted-model-keys",Context.MODE_PRIVATE)
 private fun key():SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  (store.getKey("luke-model-key",null) as? SecretKey)?.let{return it}
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("luke-model-key",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
 }
 @Synchronized fun save(value:String,slot:String="primary"){
  require(slot in setOf("primary","fallback"));require(value.length<=4096)
  if(value.isEmpty()){check(storage.edit().remove(slot).commit()){ "密钥未能删除" };return}
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
  val bytes=cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8))
  check(storage.edit().putString(slot,Base64.encodeToString(bytes,Base64.NO_WRAP)).commit()){ "密钥未能保存" }
 }
 @Synchronized fun read(slot:String="primary"):String{
  require(slot in setOf("primary","fallback"));val saved=storage.getString(slot,null)?:return ""
  val bytes=Base64.decode(saved,Base64.NO_WRAP);require(bytes.size>12)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))}
  return cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8)
 }
}

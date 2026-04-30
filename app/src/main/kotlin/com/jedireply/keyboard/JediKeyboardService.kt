package com.jedireply.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class JediKeyboardService : InputMethodService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()
    private val API_BASE = "https://app.jedichatai.com"

    private var isShifted = false
    private var currentWord = StringBuilder()
    private var fullText = StringBuilder()

    private lateinit var suggestion1: TextView
    private lateinit var suggestion2: TextView
    private lateinit var suggestion3: TextView
    private lateinit var aiStatus: TextView

    // Simple autocorrect dictionary
    private val autocorrect = mapOf(
        "teh" to "the", "adn" to "and", "hte" to "the",
        "wiht" to "with", "taht" to "that", "waht" to "what",
        "hwo" to "how", "yuo" to "you", "tahnk" to "thank",
        "fo" to "of", "ot" to "to", "si" to "is",
        "ti" to "it", "od" to "do", "on" to "on"
    )

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)

        suggestion1 = view.findViewById(R.id.suggestion1)
        suggestion2 = view.findViewById(R.id.suggestion2)
        suggestion3 = view.findViewById(R.id.suggestion3)
        aiStatus = view.findViewById(R.id.ai_status)

        setupKeys(view)
        setupSuggestionClicks()

        return view
    }

    private fun setupKeys(view: View) {
        val letterKeys = mapOf(
            R.id.key_q to "q", R.id.key_w to "w", R.id.key_e to "e",
            R.id.key_r to "r", R.id.key_t to "t", R.id.key_y to "y",
            R.id.key_u to "u", R.id.key_i to "i", R.id.key_o to "o",
            R.id.key_p to "p", R.id.key_a to "a", R.id.key_s to "s",
            R.id.key_d to "d", R.id.key_f to "f", R.id.key_g to "g",
            R.id.key_h to "h", R.id.key_j to "j", R.id.key_k to "k",
            R.id.key_l to "l", R.id.key_z to "z", R.id.key_x to "x",
            R.id.key_c to "c", R.id.key_v to "v", R.id.key_b to "b",
            R.id.key_n to "n", R.id.key_m to "m"
        )

        letterKeys.forEach { (id, letter) ->
            view.findViewById<Button>(id)?.setOnClickListener {
                val char = if (isShifted) letter.uppercase() else letter
                currentInputConnection?.commitText(char, 1)
                currentWord.append(char)
                fullText.append(char)
                if (isShifted) { isShifted = false; updateShiftState(view) }
                updateSuggestionsLocal()
            }
        }

        view.findViewById<Button>(R.id.key_space)?.setOnClickListener {
            val word = currentWord.toString().lowercase()
            val corrected = autocorrect[word]
            if (corrected != null) {
                // Delete current word and replace with corrected
                repeat(currentWord.length) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                currentInputConnection?.commitText("$corrected ", 1)
                fullText.delete(fullText.length - currentWord.length, fullText.length)
                fullText.append("$corrected ")
            } else {
                currentInputConnection?.commitText(" ", 1)
                fullText.append(" ")
            }
            currentWord.clear()
            fetchAISuggestions()
        }

        view.findViewById<Button>(R.id.key_delete)?.setOnClickListener {
            currentInputConnection?.deleteSurroundingText(1, 0)
            if (currentWord.isNotEmpty()) currentWord.deleteCharAt(currentWord.length - 1)
            if (fullText.isNotEmpty()) fullText.deleteCharAt(fullText.length - 1)
            updateSuggestionsLocal()
        }

        view.findViewById<Button>(R.id.key_return)?.setOnClickListener {
            currentInputConnection?.commitText("\n", 1)
            currentWord.clear()
            fullText.append("\n")
        }

        view.findViewById<Button>(R.id.key_shift)?.setOnClickListener {
            isShifted = !isShifted
            updateShiftState(view)
        }

        view.findViewById<Button>(R.id.key_123)?.setOnClickListener {
            // Switch to numbers - basic implementation
            currentInputConnection?.commitText("1", 1)
        }
    }

    private fun setupSuggestionClicks() {
        suggestion1.setOnClickListener { insertSuggestion(suggestion1.text.toString()) }
        suggestion2.setOnClickListener { insertSuggestion(suggestion2.text.toString()) }
        suggestion3.setOnClickListener { insertSuggestion(suggestion3.text.toString()) }
    }

    private fun insertSuggestion(word: String) {
        // Delete current partial word
        if (currentWord.isNotEmpty()) {
            repeat(currentWord.length) {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }
        currentInputConnection?.commitText("$word ", 1)
        currentWord.clear()
        fullText.append("$word ")
        fetchAISuggestions()
    }

    private fun updateShiftState(view: View) {
        val shiftBtn = view.findViewById<Button>(R.id.key_shift)
        shiftBtn?.setBackgroundResource(
            if (isShifted) R.drawable.key_bg else R.drawable.key_special_bg
        )
    }

    private fun updateSuggestionsLocal() {
        val word = currentWord.toString().lowercase()
        if (word.isEmpty()) {
            setSuggestions("It", "The", "I")
            return
        }
        // Basic prefix suggestions
        val common = listOf("the", "this", "that", "they", "them", "there", "then",
            "and", "are", "all", "also", "about", "you", "your", "yes",
            "is", "in", "it", "if", "I", "on", "of", "or", "ok", "okay")
        val matches = common.filter { it.startsWith(word) && it != word }.take(3)
        when (matches.size) {
            0 -> setSuggestions(word, word.capitalize(), "${word}s")
            1 -> setSuggestions(word, matches[0], "${word}ing")
            2 -> setSuggestions(word, matches[0], matches[1])
            else -> setSuggestions(matches[0], matches[1], matches[2])
        }
    }

    private fun fetchAISuggestions() {
        val text = fullText.toString().takeLast(200)
        if (text.trim().length < 3) return

        aiStatus.text = "Thinking..."

        scope.launch {
            try {
                val prefs = getSharedPreferences("jedikeyboard", Context.MODE_PRIVATE)
                val token = prefs.getString("token", "") ?: ""

                val json = JSONObject().apply {
                    put("message", "Complete this message naturally with 3 short word suggestions (respond with just 3 words separated by |): $text")
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$API_BASE/api/v1/keyboard/suggest")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                val data = JSONObject(responseBody)
                val suggestions = data.optJSONArray("suggestions")

                mainHandler.post {
                    aiStatus.text = "Ready"
                    if (suggestions != null && suggestions.length() > 0) {
                        val text1 = suggestions.optJSONObject(0)?.optString("text", "") ?: ""
                        val words = text1.split("|", " ").filter { it.isNotBlank() }.take(3)
                        when (words.size) {
                            0 -> {}
                            1 -> setSuggestions(words[0], "", "")
                            2 -> setSuggestions(words[0], words[1], "")
                            else -> setSuggestions(words[0], words[1], words[2])
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { aiStatus.text = "Ready" }
            }
        }
    }

    private fun setSuggestions(s1: String, s2: String, s3: String) {
        suggestion1.text = s1
        suggestion2.text = s2
        suggestion3.text = s3
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentWord.clear()
        fullText.clear()
        setSuggestions("It", "The", "I")
        aiStatus.text = "Ready"
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

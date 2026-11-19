package org.citra.citra_emu.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.citra.citra_emu.utils.NetPlayManager

class DpadChatOverlayView(context: Context) : FrameLayout(context) {

    data class ChatLine(
        val sender: String,
        val text: String,
        val isSystem: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    var isDrawerOpen: Boolean = false
        private set

    private val messages = mutableListOf<ChatLine>()
    private val maxMessages = 60
    private val fadeDurationMs = 6500L
    private val uiHandler = Handler(Looper.getMainLooper())

    private val passiveFeedContainer: LinearLayout
    private val toggleButton: TextView
    private val drawerPanel: LinearLayout
    private val historyScrollView: ScrollView
    private val historyContainer: LinearLayout
    private val inputPreviewText: TextView
    private val quickChatRow: LinearLayout
    private val keyboardGrid: GridLayout

    private val currentBuffer = StringBuilder()
    private var isCaps = false
    private val letterButtons = mutableListOf<Button>()

    private val quickPhrases = listOf(
        "Ready!", "Let's go!", "One sec...", "Nice!", "Thanks!", "GG!"
    )

    private val keyRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "DEL"),
        listOf("CAPS", "z", "x", "c", "v", "b", "n", "m", "SPACE", "SEND")
    )

    private val fadeRunnable = object : Runnable {
        override fun run() {
            refreshPassiveFeed()
            uiHandler.postDelayed(this, 500L)
        }
    }

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        isClickable = false
        isFocusable = false

        passiveFeedContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
            isClickable = false
            isFocusable = false
        }
        val passiveParams = LayoutParams(dp(340), LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        addView(passiveFeedContainer, passiveParams)

        toggleButton = TextView(context).apply {
            text = "CHAT"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#88000000"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#66FFFFFF"))
            }
            setOnClickListener {
                if (isDrawerOpen) closeDrawer() else openDrawer()
            }
        }
        val toggleParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(12)
            marginEnd = dp(14)
        }
        addView(toggleButton, toggleParams)

        drawerPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC121212"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#44FFFFFF"))
            }
        }
        val drawerParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(dp(16), dp(12), dp(16), dp(12))
        }

        historyContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        historyScrollView = ScrollView(context).apply {
            addView(historyContainer)
        }
        drawerPanel.addView(historyScrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(110)
        ))

        inputPreviewText = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF222222"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#88FFFFFF"))
            }
        }
        val inputParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        }
        drawerPanel.addView(inputPreviewText, inputParams)

        quickChatRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val quickScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(quickChatRow)
        }
        buildQuickChatButtons()
        drawerPanel.addView(quickScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        keyboardGrid = GridLayout(context).apply {
            columnCount = 10
            rowCount = 4
        }
        buildKeyboardGrid()
        drawerPanel.addView(keyboardGrid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        addView(drawerPanel, drawerParams)
        updateInputPreview()
        uiHandler.post(fadeRunnable)
    }

    fun onChatReceived(sender: String, body: String) {
        uiHandler.post {
            addMessage(ChatLine(sender, body, isSystem = (sender == "System")))
        }
    }

    fun onRoomMessage(type: Int, rawMessage: String) {
        uiHandler.post {
            when (type) {
                NetPlayManager.NetPlayStatus.CHAT_MESSAGE -> {
                    val splitIdx = rawMessage.indexOf(':')
                    val sender = if (splitIdx > 0) rawMessage.substring(0, splitIdx).trim() else "Player"
                    val body = if (splitIdx > 0) rawMessage.substring(splitIdx + 1).trim() else rawMessage
                    addMessage(ChatLine(sender, body, isSystem = false))
                }
                NetPlayManager.NetPlayStatus.MEMBER_JOIN ->
                    addMessage(ChatLine("System", "$rawMessage joined the room", isSystem = true))
                NetPlayManager.NetPlayStatus.MEMBER_LEAVE ->
                    addMessage(ChatLine("System", "$rawMessage left the room", isSystem = true))
                NetPlayManager.NetPlayStatus.MEMBER_KICKED ->
                    addMessage(ChatLine("System", "$rawMessage was kicked", isSystem = true))
                NetPlayManager.NetPlayStatus.MEMBER_BANNED ->
                    addMessage(ChatLine("System", "$rawMessage was banned", isSystem = true))
            }
        }
    }

    private fun addMessage(line: ChatLine) {
        messages.add(line)
        if (messages.size > maxMessages) {
            messages.removeAt(0)
        }
        refreshPassiveFeed()
        if (isDrawerOpen) {
            refreshDrawerHistory()
        }
    }

    private fun refreshPassiveFeed() {
        passiveFeedContainer.removeAllViews()
        if (isDrawerOpen) return
        val now = System.currentTimeMillis()
        val recent = messages.filter { now - it.timestamp < fadeDurationMs }.takeLast(5)
        for (msg in recent) {
            val tv = formatMessageTextView(msg, alpha = 1f)
            val age = now - msg.timestamp
            if (age > fadeDurationMs - 1000L) {
                tv.alpha = ((fadeDurationMs - age).coerceAtLeast(0L) / 1000f)
            }
            passiveFeedContainer.addView(tv)
        }
    }

    private fun refreshDrawerHistory() {
        historyContainer.removeAllViews()
        for (msg in messages) {
            historyContainer.addView(formatMessageTextView(msg, alpha = 1f))
        }
        historyScrollView.post { historyScrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun formatMessageTextView(msg: ChatLine, alpha: Float): TextView {
        val sb = SpannableStringBuilder()
        if (msg.isSystem) {
            sb.append("* ${msg.text}")
            sb.setSpan(ForegroundColorSpan(Color.LTGRAY), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.ITALIC), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            sb.append("${msg.sender}: ")
            sb.setSpan(ForegroundColorSpan(Color.parseColor("#4FC3F7")), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val start = sb.length
            sb.append(msg.text)
            sb.setSpan(ForegroundColorSpan(Color.WHITE), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return TextView(context).apply {
            text = sb
            this.alpha = alpha
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setShadowLayer(3f, 1.5f, 1.5f, Color.BLACK)
            setPadding(0, dp(2), 0, dp(2))
            isClickable = false
            isFocusable = false
        }
    }

    private fun buildQuickChatButtons() {
        quickChatRow.removeAllViews()
        for (phrase in quickPhrases) {
            val btn = Button(context).apply {
                text = phrase
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(12), dp(4), dp(12), dp(4))
                isFocusable = true
                background = createKeyFocusDrawable("#2A3B4C", "#FFFFFF")
                setTextColor(createKeyTextColors())
                setOnClickListener {
                    sendMessageText(phrase)
                    closeDrawer()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)
            ).apply { marginEnd = dp(6) }
            quickChatRow.addView(btn, lp)
        }
    }

    private fun buildKeyboardGrid() {
        keyboardGrid.removeAllViews()
        letterButtons.clear()
        for ((rIdx, row) in keyRows.withIndex()) {
            val rowViews = mutableListOf<Button>()
            for ((cIdx, label) in row.withIndex()) {
                val btn = Button(context).apply {
                    text = label
                    isAllCaps = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 1) 11f else 14f)
                    setPadding(0, 0, 0, 0)
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    isFocusable = true
                    id = View.generateViewId()
                    background = createKeyFocusDrawable("#2C2C2C", "#FFFFFF")
                    setTextColor(createKeyTextColors())
                    setOnClickListener { onVirtualKeyPressed(label) }
                }
                if (label.length == 1 && label[0].isLetter()) {
                    letterButtons.add(btn)
                }
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(rIdx),
                    GridLayout.spec(cIdx, 1f)
                ).apply {
                    width = 0
                    height = dp(36)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                keyboardGrid.addView(btn, params)
                rowViews.add(btn)
            }
            rowViews.first().nextFocusLeftId = rowViews.last().id
            rowViews.last().nextFocusRightId = rowViews.first().id
        }
    }

    private fun onVirtualKeyPressed(key: String) {
        when (key) {
            "DEL" -> if (currentBuffer.isNotEmpty()) currentBuffer.deleteCharAt(currentBuffer.length - 1)
            "SPACE" -> currentBuffer.append(' ')
            "CAPS" -> {
                isCaps = !isCaps
                for (btn in letterButtons) {
                    val ch = btn.text.toString()
                    btn.text = if (isCaps) ch.uppercase() else ch.lowercase()
                }
            }
            "SEND" -> {
                val textToSend = currentBuffer.toString().trim()
                if (textToSend.isNotEmpty()) {
                    sendMessageText(textToSend)
                    currentBuffer.clear()
                }
                closeDrawer()
            }
            else -> currentBuffer.append(if (isCaps) key.uppercase() else key.lowercase())
        }
        updateInputPreview()
    }

    private fun sendMessageText(text: String) {
        if (NetPlayManager.netPlayIsJoined()) {
            NetPlayManager.netPlaySendMessage(text)
            addMessage(ChatLine("You", text, isSystem = false))
        } else {
            addMessage(ChatLine("Local Preview", text, isSystem = false))
        }
    }

    private fun updateInputPreview() {
        val display = if (currentBuffer.isEmpty()) "Type with D-pad (X=Del, Y=Space, B=Close)..." else "${currentBuffer}_"
        inputPreviewText.text = display
    }

    fun openDrawer() {
        if (isDrawerOpen) return
        isDrawerOpen = true
        passiveFeedContainer.visibility = View.GONE
        drawerPanel.visibility = View.VISIBLE
        refreshDrawerHistory()
        updateInputPreview()
        quickChatRow.getChildAt(0)?.requestFocus()
    }

    fun closeDrawer() {
        if (!isDrawerOpen) return
        isDrawerOpen = false
        drawerPanel.visibility = View.GONE
        passiveFeedContainer.visibility = View.VISIBLE
        refreshPassiveFeed()
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isDrawerOpen) return false
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { closeDrawer(); return true }
                KeyEvent.KEYCODE_BUTTON_X -> { onVirtualKeyPressed("DEL"); return true }
                KeyEvent.KEYCODE_BUTTON_Y -> { onVirtualKeyPressed("SPACE"); return true }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = isDrawerOpen

    private fun createKeyFocusDrawable(normalHex: String, focusedHex: String): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                setColor(Color.parseColor(focusedHex))
                cornerRadius = dp(6).toFloat()
            })
            addState(intArrayOf(), GradientDrawable().apply {
                setColor(Color.parseColor(normalHex))
                cornerRadius = dp(6).toFloat()
            })
        }
    }

    private fun createKeyTextColors(): ColorStateList {
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(Color.BLACK, Color.WHITE)
        )
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
    }
}

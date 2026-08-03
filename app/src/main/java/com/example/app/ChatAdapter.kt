package com.example.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.TextContent

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_message_item, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageContainer: View = itemView.findViewById(R.id.messageContainer)

        fun bind(message: ChatMessage) {
            val content = (message.messageContent as? TextContent)?.content ?: message.content.orEmpty()

            messageText.text = content

            // 메시지 타입에 따라 스타일 변경
            when (message.role) {
                ChatRole.User -> {
                    messageText.setBackgroundResource(R.drawable.bg_chat_user)
                    messageText.setTextColor(itemView.context.getColor(R.color.white))
                    val layoutParams = messageContainer.layoutParams as LinearLayout.LayoutParams
                    layoutParams.gravity = android.view.Gravity.END
                    messageContainer.layoutParams = layoutParams
                }
                ChatRole.Assistant -> {
                    messageText.setBackgroundResource(R.drawable.bg_chat_ai)
                    messageText.setTextColor(itemView.context.getColor(R.color.figma_black))
                    val layoutParams = messageContainer.layoutParams as LinearLayout.LayoutParams
                    layoutParams.gravity = android.view.Gravity.START
                    messageContainer.layoutParams = layoutParams
                }
            }
        }
    }
}
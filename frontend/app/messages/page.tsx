"use client";

import { useEffect, useState, useRef, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { messages as messagesApi } from "@/lib/api";
import type { Conversation, ChatMessage } from "@/lib/types";
import { MessageCircle, Send, AlertCircle, ChevronLeft } from "lucide-react";

function MessagesContent() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const propertyId = searchParams.get("propertyId");

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConv, setActiveConv] = useState<Conversation | null>(null);
  const [threadMessages, setThreadMessages] = useState<ChatMessage[]>([]);
  const [newMessage, setNewMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/messages");
  }, [user, loading, router]);

  useEffect(() => {
    if (user && token) {
      messagesApi.listConversations(user.id, token).then(setConversations).catch(() => {});
    }
  }, [user, token]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [threadMessages]);

  const openConversation = async (conv: Conversation) => {
    if (!token) return;
    setActiveConv(conv);
    try {
      const msgs = await messagesApi.getConversation(conv.id, token);
      setThreadMessages(msgs);
      await messagesApi.markRead(conv.id, token);
    } catch {}
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeConv || !token || !newMessage.trim()) return;
    setSending(true);
    try {
      const msg = await messagesApi.send({ conversationId: activeConv.id, content: newMessage }, token);
      setThreadMessages((m) => [...m, msg]);
      setNewMessage("");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to send.");
    } finally {
      setSending(false);
    }
  };

  if (loading || !user) {
    return <div className="flex-1 flex items-center justify-center text-gray-400">Loading...</div>;
  }

  return (
    <div className="flex flex-1 h-[calc(100vh-64px)]">
      {/* Sidebar: conversation list */}
      <div className={`w-full sm:w-72 border-r border-gray-200 flex flex-col ${activeConv ? "hidden sm:flex" : "flex"}`}>
        <div className="p-4 border-b border-gray-200">
          <h1 className="font-bold text-gray-900 flex items-center gap-2">
            <MessageCircle className="w-5 h-5 text-blue-600" /> Messages
          </h1>
        </div>

        {error && (
          <div className="mx-3 mt-3 bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-xl flex items-center gap-2">
            <AlertCircle className="w-3 h-3" /> {error}
          </div>
        )}

        <div className="flex-1 overflow-y-auto">
          {conversations.length === 0 ? (
            <div className="text-center py-12 text-gray-400 px-4">
              <MessageCircle className="w-8 h-8 mx-auto mb-3 opacity-30" />
              <p className="text-sm">No conversations yet</p>
              <p className="text-xs mt-1">Start by messaging a landlord from a property page</p>
            </div>
          ) : (
            conversations.map((conv) => (
              <button
                key={conv.id}
                onClick={() => openConversation(conv)}
                className={`w-full text-left px-4 py-3 border-b border-gray-100 hover:bg-gray-50 transition-colors ${activeConv?.id === conv.id ? "bg-blue-50" : ""}`}
              >
                <p className="text-sm font-medium text-gray-900 truncate">{conv.subject || "Conversation"}</p>
                {conv.lastMessage && <p className="text-xs text-gray-500 truncate mt-0.5">{conv.lastMessage}</p>}
                {conv.unreadCount != null && conv.unreadCount > 0 && (
                  <span className="inline-block mt-1 text-xs bg-blue-600 text-white px-2 py-0.5 rounded-full">
                    {conv.unreadCount}
                  </span>
                )}
              </button>
            ))
          )}
        </div>
      </div>

      {/* Thread */}
      <div className={`flex-1 flex flex-col ${!activeConv ? "hidden sm:flex items-center justify-center text-gray-400" : ""}`}>
        {!activeConv ? (
          <div className="text-center">
            <MessageCircle className="w-12 h-12 mx-auto mb-4 opacity-30" />
            <p>Select a conversation</p>
          </div>
        ) : (
          <>
            <div className="px-4 py-3 border-b border-gray-200 flex items-center gap-3">
              <button onClick={() => setActiveConv(null)} className="sm:hidden text-gray-500">
                <ChevronLeft className="w-5 h-5" />
              </button>
              <p className="font-semibold text-gray-900">{activeConv.subject || "Conversation"}</p>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {threadMessages.map((msg) => {
                const isMe = msg.senderId === user.id;
                return (
                  <div key={msg.id} className={`flex ${isMe ? "justify-end" : "justify-start"}`}>
                    <div className={`max-w-xs sm:max-w-md rounded-2xl px-4 py-2.5 text-sm ${isMe ? "bg-blue-600 text-white" : "bg-gray-100 text-gray-800"}`}>
                      {msg.messageType && msg.messageType !== "GENERAL" && (
                        <p className={`text-xs mb-1 font-medium ${isMe ? "text-blue-200" : "text-gray-400"}`}>
                          {msg.messageType.replace("_", " ")}
                        </p>
                      )}
                      {msg.content}
                    </div>
                  </div>
                );
              })}
              <div ref={bottomRef} />
            </div>

            <form onSubmit={handleSend} className="p-4 border-t border-gray-200 flex gap-3">
              <input
                value={newMessage}
                onChange={(e) => setNewMessage(e.target.value)}
                placeholder="Type a message..."
                className="flex-1 border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
              <button
                type="submit"
                disabled={sending || !newMessage.trim()}
                className="w-10 h-10 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl flex items-center justify-center transition-colors"
              >
                <Send className="w-4 h-4" />
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}

export default function MessagesPage() {
  return (
    <Suspense>
      <MessagesContent />
    </Suspense>
  );
}

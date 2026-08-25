"use client";

import { useState } from "react";
import { messages as messagesApi } from "@/lib/api";
import type { AdminConversation, AdminMessage, User } from "@/lib/types";
import EntityPicker from "./EntityPicker";
import { MessageSquare, Send, Plus, X } from "lucide-react";

interface AdminMessagesPanelProps {
  token: string;
  currentUserId: number;
  allUsers: User[];
  conversations: AdminConversation[];
  loading: boolean;
  onRefresh: () => void;
}

export default function AdminMessagesPanel({
  token,
  currentUserId,
  allUsers,
  conversations,
  loading,
  onRefresh,
}: AdminMessagesPanelProps) {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [thread, setThread] = useState<AdminMessage[]>([]);
  const [threadLoading, setThreadLoading] = useState(false);
  const [error, setError] = useState("");

  const [showNewConversation, setShowNewConversation] = useState(false);
  const [recipientId, setRecipientId] = useState<number | null>(null);
  const [subject, setSubject] = useState("");
  const [content, setContent] = useState("");
  const [sending, setSending] = useState(false);

  const [reply, setReply] = useState("");
  const [sendingReply, setSendingReply] = useState(false);

  const selected = conversations.find((c) => c.id === selectedId) ?? null;
  const isParticipant = selected ? selected.participantIds.includes(currentUserId) : true;

  const openConversation = async (id: number) => {
    setSelectedId(id);
    setShowNewConversation(false);
    setThreadLoading(true);
    setError("");
    try {
      const msgs = await messagesApi.adminGetConversation(id, token);
      setThread(msgs);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load conversation.");
    } finally {
      setThreadLoading(false);
    }
  };

  const handleStartConversation = async () => {
    if (!recipientId || !subject.trim() || !content.trim()) {
      setError("Select a recipient and fill in subject and message.");
      return;
    }
    setSending(true);
    setError("");
    try {
      const conv = await messagesApi.startConversation(
        { recipientId, subject: subject.trim(), content: content.trim(), messageType: "GENERAL" },
        token
      );
      setShowNewConversation(false);
      setRecipientId(null);
      setSubject("");
      setContent("");
      onRefresh();
      await openConversation(conv.id);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to start conversation.");
    } finally {
      setSending(false);
    }
  };

  const handleReply = async () => {
    if (!selectedId || !reply.trim()) return;
    setSendingReply(true);
    setError("");
    try {
      await messagesApi.send({ conversationId: selectedId, content: reply.trim() }, token);
      setReply("");
      const msgs = await messagesApi.adminGetConversation(selectedId, token);
      setThread(msgs);
      onRefresh();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to send reply.");
    } finally {
      setSendingReply(false);
    }
  };

  const userOptions = allUsers.map((u) => ({ id: u.id, label: u.fullName, sublabel: u.email }));

  return (
    <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden flex h-[70vh]">
      {/* Conversation list */}
      <div
        className={`w-full sm:w-80 border-r border-gray-200 flex flex-col ${
          selectedId || showNewConversation ? "hidden sm:flex" : "flex"
        }`}
      >
        <div className="p-3 border-b border-gray-100 flex items-center justify-between">
          <span className="font-bold text-gray-900 text-sm">All Conversations</span>
          <button
            onClick={() => {
              setShowNewConversation(true);
              setSelectedId(null);
              setError("");
            }}
            className="flex items-center gap-1 text-xs font-medium text-forest-600 hover:text-forest-700"
          >
            <Plus className="w-3.5 h-3.5" /> New
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="p-4 space-y-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="h-14 bg-gray-100 rounded-lg animate-pulse" />
              ))}
            </div>
          ) : conversations.length === 0 ? (
            <div className="text-center py-16 text-gray-400 text-sm px-4">
              <MessageSquare className="w-8 h-8 mx-auto mb-2 opacity-30" />
              No conversations on the platform yet
            </div>
          ) : (
            conversations.map((c) => (
              <button
                key={c.id}
                onClick={() => openConversation(c.id)}
                className={`w-full text-left px-4 py-3 border-b border-gray-50 hover:bg-gray-50 transition-colors ${
                  selectedId === c.id ? "bg-forest-50" : ""
                }`}
              >
                <p className="text-sm font-medium text-gray-900 truncate">{c.participantNames.join(" & ")}</p>
                {c.subject && <p className="text-xs text-gray-500 truncate">{c.subject}</p>}
                <p className="text-xs text-gray-400 truncate">{c.lastMessage ?? "No messages yet"}</p>
              </button>
            ))
          )}
        </div>
      </div>

      {/* Right column: new conversation form OR thread */}
      <div className={`flex-1 flex flex-col ${selectedId || showNewConversation ? "flex" : "hidden sm:flex"}`}>
        {error && (
          <div className="m-3 bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-lg">{error}</div>
        )}

        {showNewConversation ? (
          <div className="p-4 space-y-4 max-w-md">
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-gray-900 text-sm">New Support Conversation</h3>
              <button onClick={() => setShowNewConversation(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-4 h-4" />
              </button>
            </div>
            <EntityPicker
              label="Recipient"
              options={userOptions}
              value={recipientId}
              onChange={setRecipientId}
              placeholder="Select a user..."
            />
            <div>
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Subject</label>
              <input
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Message</label>
              <textarea
                value={content}
                onChange={(e) => setContent(e.target.value)}
                rows={4}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 resize-none"
              />
            </div>
            <button
              onClick={handleStartConversation}
              disabled={sending}
              className="w-full bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white text-sm font-semibold py-2.5 rounded-xl"
            >
              {sending ? "Starting..." : "Start Conversation"}
            </button>
          </div>
        ) : selectedId !== null ? (
          <>
            <div className="p-4 border-b border-gray-100">
              <p className="font-bold text-gray-900 text-sm">{selected?.participantNames.join(" & ") ?? "Conversation"}</p>
              {selected?.subject && <p className="text-xs text-gray-500">{selected.subject}</p>}
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {threadLoading ? (
                <div className="space-y-2">
                  {Array.from({ length: 3 }).map((_, i) => (
                    <div key={i} className="h-10 bg-gray-100 rounded-lg animate-pulse" />
                  ))}
                </div>
              ) : thread.length === 0 ? (
                <p className="text-center text-gray-400 text-sm py-8">No messages yet</p>
              ) : (
                thread.map((m) => (
                  <div key={m.id} className={`max-w-[75%] ${m.senderId === currentUserId ? "ml-auto text-right" : ""}`}>
                    <div
                      className={`inline-block px-3 py-2 rounded-xl text-sm ${
                        m.senderId === currentUserId ? "bg-forest-600 text-white" : "bg-gray-100 text-gray-900"
                      }`}
                    >
                      {m.content}
                    </div>
                    <p className="text-[10px] text-gray-400 mt-1">
                      {m.senderName} · {new Date(m.createdAt).toLocaleString()}
                    </p>
                  </div>
                ))
              )}
            </div>
            {isParticipant ? (
              <div className="p-3 border-t border-gray-100 flex gap-2">
                <input
                  value={reply}
                  onChange={(e) => setReply(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") handleReply();
                  }}
                  placeholder="Reply..."
                  className="flex-1 border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500"
                />
                <button
                  onClick={handleReply}
                  disabled={sendingReply || !reply.trim()}
                  className="bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-4 rounded-xl flex items-center justify-center"
                >
                  <Send className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <div className="p-3 border-t border-gray-100 bg-amber-50 text-amber-700 text-xs text-center">
                Viewing for oversight — you are not a participant in this conversation
              </div>
            )}
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
            Select a conversation or start a new one
          </div>
        )}
      </div>
    </div>
  );
}

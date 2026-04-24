import React, { useEffect, useRef, useState } from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { useColors, useSpacing, useTypography } from '../../theme';
import { useAuthStore } from '../../store/auth.store';
import { aiService, ChatMessage } from '../../services/ai.service';

// ─── Constants ────────────────────────────────────────────────────────────────

const SUGGESTED_QUESTIONS = [
  'Where am I overspending?',
  'Can I afford Ksh 5,000 this week?',
  'How are my savings?',
  'Give me a spending summary',
  'What is my biggest expense this month?',
];

// ─── Message Bubble ───────────────────────────────────────────────────────────

function MessageBubble({
  message,
  isLast,
}: {
  message: ChatMessage & { id: string };
  isLast:  boolean;
}) {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const isUser     = message.role === 'user';

  return (
    <View style={{
      flexDirection:  isUser ? 'row-reverse' : 'row',
      alignItems:     'flex-end',
      gap:            spacing[2],
      marginBottom:   spacing[3],
    }}>
      {/* AI avatar */}
      {!isUser && (
        <View style={{
          width:           28,
          height:          28,
          borderRadius:    14,
          backgroundColor: colors.gold,
          alignItems:      'center',
          justifyContent:  'center',
          marginBottom:    spacing[1],
        }}>
          <Text style={{
            color:      '#FFFFFF',
            fontSize:   typography.size.xs,
            fontWeight: typography.weight.bold,
          }}>
            AI
          </Text>
        </View>
      )}

      {/* Bubble */}
      <View style={{
        maxWidth:          '75%',
        backgroundColor:   isUser ? colors.primary : colors.surface,
        borderRadius:      16,
        borderBottomRightRadius: isUser ? 4  : 16,
        borderBottomLeftRadius:  isUser ? 16 : 4,
        padding:           spacing[3],
        shadowColor:       colors.cardShadow,
        shadowOffset:      { width: 0, height: 1 },
        shadowOpacity:     1,
        shadowRadius:      4,
        elevation:         2,
      }}>
        <Text style={{
          color:      isUser ? '#FFFFFF' : colors.textPrimary,
          fontSize:   typography.size.sm,
          lineHeight: typography.size.sm * 1.5,
        }}>
          {message.content}
        </Text>
      </View>
    </View>
  );
}

// ─── Typing Indicator ─────────────────────────────────────────────────────────

function TypingIndicator() {
  const colors  = useColors();
  const spacing = useSpacing();

  return (
    <View style={{
      flexDirection: 'row',
      alignItems:    'flex-end',
      gap:           spacing[2],
      marginBottom:  spacing[3],
    }}>
      {/* AI avatar */}
      <View style={{
        width:           28,
        height:          28,
        borderRadius:    14,
        backgroundColor: colors.gold,
        alignItems:      'center',
        justifyContent:  'center',
      }}>
        <Text style={{ color: '#FFFFFF', fontSize: 10, fontWeight: '700' }}>AI</Text>
      </View>

      {/* Dots */}
      <View style={{
        backgroundColor: colors.surface,
        borderRadius:    16,
        borderBottomLeftRadius: 4,
        padding:         spacing[3],
        flexDirection:   'row',
        gap:             6,
        alignItems:      'center',
      }}>
        {[colors.primary, colors.secondary, colors.accentGreen].map((color, i) => (
          <View
            key={i}
            style={{
              width:           8,
              height:          8,
              borderRadius:    4,
              backgroundColor: color,
              opacity:         0.7,
            }}
          />
        ))}
      </View>
    </View>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function AiChatScreen() {
  const colors     = useColors();
  const spacing    = useSpacing();
  const typography = useTypography();
  const user       = useAuthStore((state) => state.user);
  const listRef    = useRef<FlatList>(null);

  const [messages,  setMessages]  = useState<(ChatMessage & { id: string })[]>([]);
  const [input,     setInput]     = useState('');
  const [isTyping,  setIsTyping]  = useState(false);
  const [error,     setError]     = useState<string | null>(null);

  const firstName = user?.fullName?.split(' ')[0] ?? 'there';

  // ── Welcome message after 500ms ────────────────────────────────────────────

  useEffect(() => {
    const timer = setTimeout(() => {
      setMessages([{
        id:      'welcome',
        role:    'assistant',
        content: `Hello ${firstName}! Every shilling has a story. I have analyzed your finances. Ask me anything — I am here to help you save smarter.`,
      }]);
    }, 500);
    return () => clearTimeout(timer);
  }, []);

  // ── Auto scroll to bottom on new message ──────────────────────────────────

  useEffect(() => {
    if (messages.length > 0) {
      setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 100);
    }
  }, [messages, isTyping]);

  // ── Send message ───────────────────────────────────────────────────────────

  const handleSend = async (text?: string) => {
    const content = (text ?? input).trim();
    if (!content) return;

    const userMessage: ChatMessage & { id: string } = {
      id:      `user-${Date.now()}`,
      role:    'user',
      content,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsTyping(true);
    setError(null);

    try {
      const history: ChatMessage[] = messages.map(({ role, content }) => ({ role, content }));
      const response = await aiService.chat([...history, { role: 'user', content }]);

      setMessages((prev) => [...prev, {
        id:      `ai-${Date.now()}`,
        role:    'assistant',
        content: response.message,
      }]);
    } catch (err: any) {
      setError(err.message ?? 'Failed to get response. Tap to retry.');
    } finally {
      setIsTyping(false);
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={90}
    >
      <StatusBar style="auto" />

      {/* Header */}
      <View style={{
        backgroundColor:   colors.primary,
        paddingHorizontal: spacing[4],
        paddingTop:        60,
        paddingBottom:     spacing[4],
        flexDirection:     'row',
        alignItems:        'center',
        gap:               spacing[3],
      }}>
        <View style={{
          width:           40,
          height:          40,
          borderRadius:    20,
          backgroundColor: colors.gold,
          alignItems:      'center',
          justifyContent:  'center',
        }}>
          <Text style={{ color: '#FFFFFF', fontWeight: typography.weight.bold }}>AI</Text>
        </View>
        <View>
          <Text style={{
            color:      '#FFFFFF',
            fontSize:   typography.size.md,
            fontWeight: typography.weight.bold,
          }}>
            Akiba AI
          </Text>
          <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: typography.size.xs }}>
            Your financial assistant
          </Text>
        </View>
      </View>

      {/* Messages */}
      <FlatList
        ref={listRef}
        data={messages}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{
          paddingHorizontal: spacing[4],
          paddingTop:        spacing[4],
          paddingBottom:     spacing[4],
        }}
        ListHeaderComponent={
          messages.length === 0 ? (
            <View style={{ gap: spacing[4], marginBottom: spacing[4] }}>
              <Text style={{
                color:      colors.textMuted,
                fontSize:   typography.size.sm,
                textAlign:  'center',
              }}>
                Ask me anything about your finances
              </Text>
              {/* Suggested questions */}
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View style={{ flexDirection: 'row', gap: spacing[2] }}>
                  {SUGGESTED_QUESTIONS.map((q) => (
                    <Pressable
                      key={q}
                      onPress={() => handleSend(q)}
                      style={{
                        paddingHorizontal: spacing[4],
                        paddingVertical:   spacing[2],
                        borderRadius:      20,
                        borderWidth:       1.5,
                        borderColor:       colors.primary,
                        backgroundColor:   colors.primary + '10',
                      }}
                    >
                      <Text style={{
                        color:    colors.primary,
                        fontSize: typography.size.sm,
                        fontWeight: typography.weight.medium,
                      }}>
                        {q}
                      </Text>
                    </Pressable>
                  ))}
                </View>
              </ScrollView>
            </View>
          ) : null
        }
        renderItem={({ item, index }) => (
          <MessageBubble
            message={item}
            isLast={index === messages.length - 1}
          />
        )}
        ListFooterComponent={
          <>
            {isTyping && <TypingIndicator />}
            {error && (
              <Pressable onPress={() => handleSend()}>
                <Text style={{
                  color:     colors.danger,
                  fontSize:  typography.size.sm,
                  textAlign: 'center',
                  padding:   spacing[2],
                }}>
                  {error} Tap to retry.
                </Text>
              </Pressable>
            )}
          </>
        }
      />

      {/* Input bar */}
      <View style={{
        flexDirection:     'row',
        alignItems:        'center',
        gap:               spacing[2],
        paddingHorizontal: spacing[4],
        paddingVertical:   spacing[3],
        backgroundColor:   colors.surface,
        borderTopWidth:    1,
        borderTopColor:    colors.border,
      }}>
        <TextInput
          value={input}
          onChangeText={setInput}
          placeholder="Ask Akiba..."
          placeholderTextColor={colors.textMuted}
          multiline
          style={{
            flex:              1,
            backgroundColor:   colors.inputBackground,
            borderWidth:       1.5,
            borderColor:       colors.inputBorder,
            borderRadius:      20,
            paddingHorizontal: spacing[4],
            paddingVertical:   spacing[2],
            color:             colors.textPrimary,
            fontSize:          typography.size.sm,
            maxHeight:         100,
          }}
        />

        {/* Send button */}
        <Pressable
          onPress={() => handleSend()}
          style={{
            width:           44,
            height:          44,
            borderRadius:    22,
            backgroundColor: input.trim() ? colors.primary : colors.border,
            alignItems:      'center',
            justifyContent:  'center',
          }}
        >
          <Text style={{ color: '#FFFFFF', fontSize: 18 }}>↑</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

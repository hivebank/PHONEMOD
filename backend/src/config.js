require('dotenv').config();

/**
 * Configuration module for the voice agent backend.
 * Loads and validates environment variables.
 */

const config = {
  // Server configuration
  port: process.env.PORT || 3000,
  nodeEnv: process.env.NODE_ENV || 'development',

  // OpenAI configuration
  openai: {
    apiKey: process.env.OPENAI_API_KEY,
    model: 'gpt-4o-realtime-preview-2024-10-01',
    voice: 'alloy', // Options: alloy, echo, fable, onyx, nova, shimmer
    turnDetection: {
      type: 'server_vad',
      threshold: 0.5,
      prefix_padding_ms: 300,
      silence_duration_ms: 200
    }
  },

  // Google Calendar configuration
  calendar: {
    credentialsPath: process.env.GOOGLE_CALENDAR_CREDENTIALS || './credentials.json',
    calendarId: process.env.GOOGLE_CALENDAR_ID || 'primary'
  },

  // Business information
  business: {
    name: process.env.BUSINESS_NAME || 'Our Business',
    phone: process.env.BUSINESS_PHONE || '+1234567890',
    hours: process.env.BUSINESS_HOURS || '9 AM - 9 PM daily'
  },

  // System prompt
  systemPrompt: process.env.CUSTOM_SYSTEM_PROMPT || null // Will use default if null
};

/**
 * Validate required configuration
 */
function validateConfig() {
  const errors = [];

  if (!config.openai.apiKey) {
    errors.push('OPENAI_API_KEY is required');
  }

  if (errors.length > 0) {
    console.error('Configuration errors:');
    errors.forEach(error => console.error(`  - ${error}`));
    process.exit(1);
  }
}

/**
 * Get default system prompt
 */
function getSystemPrompt() {
  if (config.systemPrompt) {
    return config.systemPrompt;
  }

  return `You are a helpful receptionist for ${config.business.name}. Answer incoming calls professionally.

Your capabilities:
1. Check appointment availability
2. Book new appointments
3. Answer questions about the business
4. Transfer urgent calls (tell caller you're forwarding them)

When someone calls:
- Greet them: "Thank you for calling ${config.business.name}, how can I help you?"
- Listen carefully to their request
- Use functions to check availability and book appointments
- Confirm all details before booking
- Speak naturally and conversationally
- Keep responses concise (under 20 words when possible)

Business info:
- Name: ${config.business.name}
- Phone: ${config.business.phone}
- Hours: ${config.business.hours}

Be helpful, professional, and friendly. If you don't know something, say so honestly.`;
}

// Validate on module load
validateConfig();

module.exports = {
  ...config,
  getSystemPrompt,
  validateConfig
};

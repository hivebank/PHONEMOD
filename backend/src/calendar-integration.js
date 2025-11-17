const { google } = require('googleapis');
const fs = require('fs');
const path = require('path');
const config = require('./config');

/**
 * Google Calendar integration for appointment scheduling.
 * Provides functions for checking availability and booking appointments.
 */

let calendarClient = null;
let isInitialized = false;

/**
 * Initialize Google Calendar API client
 */
async function initializeCalendar() {
  if (isInitialized) {
    return true;
  }

  try {
    const credentialsPath = path.resolve(config.calendar.credentialsPath);

    // Check if credentials file exists
    if (!fs.existsSync(credentialsPath)) {
      console.warn('Google Calendar credentials not found. Calendar features will be disabled.');
      console.warn(`Expected credentials at: ${credentialsPath}`);
      return false;
    }

    // Load credentials
    const credentials = JSON.parse(fs.readFileSync(credentialsPath, 'utf8'));

    // Create auth client (using service account)
    const auth = new google.auth.GoogleAuth({
      credentials,
      scopes: ['https://www.googleapis.com/auth/calendar']
    });

    const authClient = await auth.getClient();

    // Create calendar client
    calendarClient = google.calendar({ version: 'v3', auth: authClient });

    isInitialized = true;
    console.log('Google Calendar initialized successfully');
    return true;

  } catch (error) {
    console.error('Failed to initialize Google Calendar:', error.message);
    console.warn('Calendar features will be disabled.');
    return false;
  }
}

/**
 * Check availability for a given date and time
 * @param {string} date - Date in YYYY-MM-DD format
 * @param {string} time - Time in HH:MM format (24-hour)
 * @returns {Promise<Object>} - Available slots and availability status
 */
async function checkAvailability(date, time) {
  if (!isInitialized) {
    return {
      available: false,
      error: 'Calendar service not initialized',
      suggestedTimes: []
    };
  }

  try {
    // Parse date and time
    const [year, month, day] = date.split('-').map(Number);
    const [hour, minute] = time.split(':').map(Number);

    const requestedTime = new Date(year, month - 1, day, hour, minute);
    const endTime = new Date(requestedTime.getTime() + 60 * 60 * 1000); // 1 hour appointment

    // Check for conflicts
    const response = await calendarClient.events.list({
      calendarId: config.calendar.calendarId,
      timeMin: requestedTime.toISOString(),
      timeMax: endTime.toISOString(),
      singleEvents: true,
      orderBy: 'startTime'
    });

    const conflicts = response.data.items || [];

    if (conflicts.length > 0) {
      // Slot not available, suggest alternatives
      const suggestedTimes = await findAvailableSlots(date);

      return {
        available: false,
        reason: 'Time slot already booked',
        suggestedTimes
      };
    }

    return {
      available: true,
      timeSlot: {
        start: requestedTime.toISOString(),
        end: endTime.toISOString()
      }
    };

  } catch (error) {
    console.error('Error checking availability:', error);
    return {
      available: false,
      error: error.message,
      suggestedTimes: []
    };
  }
}

/**
 * Find available time slots for a given date
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<Array>} - Array of available time slots
 */
async function findAvailableSlots(date) {
  if (!isInitialized) {
    return [];
  }

  try {
    const [year, month, day] = date.split('-').map(Number);

    // Business hours: 9 AM to 5 PM
    const dayStart = new Date(year, month - 1, day, 9, 0);
    const dayEnd = new Date(year, month - 1, day, 17, 0);

    // Get all events for the day
    const response = await calendarClient.events.list({
      calendarId: config.calendar.calendarId,
      timeMin: dayStart.toISOString(),
      timeMax: dayEnd.toISOString(),
      singleEvents: true,
      orderBy: 'startTime'
    });

    const bookedSlots = response.data.items || [];

    // Generate all possible hourly slots
    const allSlots = [];
    for (let hour = 9; hour < 17; hour++) {
      allSlots.push({
        start: new Date(year, month - 1, day, hour, 0),
        end: new Date(year, month - 1, day, hour + 1, 0)
      });
    }

    // Filter out booked slots
    const availableSlots = allSlots.filter(slot => {
      return !bookedSlots.some(event => {
        const eventStart = new Date(event.start.dateTime || event.start.date);
        const eventEnd = new Date(event.end.dateTime || event.end.date);
        return (slot.start >= eventStart && slot.start < eventEnd) ||
               (slot.end > eventStart && slot.end <= eventEnd);
      });
    });

    // Format for response
    return availableSlots.slice(0, 3).map(slot => ({
      time: slot.start.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
      dateTime: slot.start.toISOString()
    }));

  } catch (error) {
    console.error('Error finding available slots:', error);
    return [];
  }
}

/**
 * Book an appointment
 * @param {Object} appointmentData - Appointment details
 * @returns {Promise<Object>} - Booking confirmation
 */
async function bookAppointment({ name, phone, date, time, notes = '' }) {
  if (!isInitialized) {
    return {
      success: false,
      error: 'Calendar service not initialized'
    };
  }

  try {
    // Parse date and time
    const [year, month, day] = date.split('-').map(Number);
    const [hour, minute] = time.split(':').map(Number);

    const startTime = new Date(year, month - 1, day, hour, minute);
    const endTime = new Date(startTime.getTime() + 60 * 60 * 1000); // 1 hour

    // First check availability
    const availability = await checkAvailability(date, time);
    if (!availability.available) {
      return {
        success: false,
        error: 'Time slot not available',
        suggestedTimes: availability.suggestedTimes
      };
    }

    // Create event
    const event = {
      summary: `Appointment: ${name}`,
      description: `Phone: ${phone}\nNotes: ${notes}`,
      start: {
        dateTime: startTime.toISOString(),
        timeZone: 'America/New_York' // Adjust as needed
      },
      end: {
        dateTime: endTime.toISOString(),
        timeZone: 'America/New_York'
      },
      attendees: phone ? [{ email: `${phone.replace(/[^0-9]/g, '')}@sms.example.com` }] : [],
      reminders: {
        useDefault: false,
        overrides: [
          { method: 'email', minutes: 24 * 60 }, // 1 day before
          { method: 'popup', minutes: 60 } // 1 hour before
        ]
      }
    };

    const response = await calendarClient.events.insert({
      calendarId: config.calendar.calendarId,
      resource: event
    });

    console.log('Appointment booked:', response.data.id);

    return {
      success: true,
      appointmentId: response.data.id,
      details: {
        name,
        phone,
        dateTime: startTime.toISOString(),
        confirmationLink: response.data.htmlLink
      }
    };

  } catch (error) {
    console.error('Error booking appointment:', error);
    return {
      success: false,
      error: error.message
    };
  }
}

/**
 * Get function definitions for OpenAI Realtime API
 */
function getFunctionDefinitions() {
  return [
    {
      name: 'check_availability',
      description: 'Check if a specific date and time is available for an appointment',
      parameters: {
        type: 'object',
        properties: {
          date: {
            type: 'string',
            description: 'Date in YYYY-MM-DD format'
          },
          time: {
            type: 'string',
            description: 'Time in HH:MM format (24-hour)'
          }
        },
        required: ['date', 'time']
      }
    },
    {
      name: 'book_appointment',
      description: 'Book an appointment for a customer',
      parameters: {
        type: 'object',
        properties: {
          name: {
            type: 'string',
            description: 'Customer name'
          },
          phone: {
            type: 'string',
            description: 'Customer phone number'
          },
          date: {
            type: 'string',
            description: 'Date in YYYY-MM-DD format'
          },
          time: {
            type: 'string',
            description: 'Time in HH:MM format (24-hour)'
          },
          notes: {
            type: 'string',
            description: 'Additional notes or reason for appointment'
          }
        },
        required: ['name', 'phone', 'date', 'time']
      }
    }
  ];
}

/**
 * Handle function calls from OpenAI
 */
async function handleFunctionCall(functionName, args) {
  console.log(`Executing function: ${functionName}`, args);

  switch (functionName) {
    case 'check_availability':
      return await checkAvailability(args.date, args.time);

    case 'book_appointment':
      return await bookAppointment(args);

    default:
      return { error: `Unknown function: ${functionName}` };
  }
}

module.exports = {
  initializeCalendar,
  checkAvailability,
  bookAppointment,
  getFunctionDefinitions,
  handleFunctionCall
};

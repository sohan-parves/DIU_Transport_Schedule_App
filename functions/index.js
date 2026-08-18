const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Firestore path: admin_messages/{docId}
 * Expected fields:
 *  - title: string
 *  - body: string
 *  - target: "diu_admin" (topic name)  // optional, default diu_admin
 *  - createdAt: timestamp              // optional
 */
exports.pushAdminMessage = functions.firestore
  .document("admin_messages/{docId}")
  .onCreate(async (snap, context) => {
    const data = snap.data() || {};

    const title = (data.title || "DIU Transport Schedule").toString();
    const body = (data.body || data.message || "").toString();
    if (!body.trim()) {
      console.log("No body found, skipping push.");
      return null;
    }

    const topic = (data.target || "diu_admin").toString(); // topic name

    const message = {
      topic,
      notification: {
        title,
        body,
      },
      data: {
        title,
        body,
        docId: context.params.docId,
      },
      android: {
        priority: "high",
        notification: {
          channelId: "admin_updates",
        },
      },
    };

    try {
      const res = await admin.messaging().send(message);
      console.log("FCM sent:", res);
      return null;
    } catch (e) {
      console.error("FCM error:", e);
      return null;
    }
  });
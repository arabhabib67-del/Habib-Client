# Hosted beta access

The mod asks each Minecraft account for its own beta password. The verifier stores only salted password hashes in Cloudflare D1; the passwords and admin token are not included in either client jar. The admin page can add or reset a password, list account UUIDs, and revoke access.

## Deploy the verifier

Use a Cloudflare account with Workers and D1 enabled. The verifier uses PBKDF2-SHA-256 with 310,000 rounds for password hashes. Cloudflare's free Workers plan allows 10 ms of CPU per request, so use the Workers Paid plan for this verifier; it provides a 30 second default CPU limit. See Cloudflare's [Workers limits](https://developers.cloudflare.com/workers/platform/limits/) and [Web Crypto support](https://developers.cloudflare.com/workers/runtime-apis/web-crypto/).

From the repository:

1. Open a terminal in `beta-access/worker` and sign in to Wrangler:

   ```sh
   npx wrangler login
   ```

2. Create the D1 database:

   ```sh
   npx wrangler d1 create habib-client-beta-access
   ```

   Copy the `database_id` Wrangler prints into `wrangler.toml`, replacing `REPLACE_WITH_D1_DATABASE_ID`.

3. Create the database tables by applying the migration:

   ```sh
   npx wrangler d1 migrations apply habib-client-beta-access --remote
   ```

4. Generate a long, random admin token in a password manager, then store it as a Cloudflare Worker secret:

   ```sh
   npx wrangler secret put BETA_ADMIN_TOKEN
   ```

   Wrangler prompts for the value. Keep it out of Git and do not give it to testers.

5. Deploy the Worker:

   ```sh
   npx wrangler deploy
   ```

   Wrangler prints an HTTPS `workers.dev` URL. Open that URL to use the admin page. Enter the admin token, then add each tester's Minecraft UUID and a unique password of at least 12 characters. Passwords cannot be retrieved after saving; reset one to issue a replacement. Use **Revoke** to remove access.

## Configure and release the client

Before building a tester release, set `beta_access_url` in both `gradle.properties` files to the Worker origin printed by Wrangler, with no path (for example, `https://habib-client-beta-access.example.workers.dev`). Do not put the admin token there. Then raise `mod_version` in both files, keeping the values identical, and push the change; the repository's GitHub Actions workflow builds and publishes both Minecraft jars.

The access screen shows each tester their Minecraft account UUID and has a **Copy ID** button. Add that UUID and password through the admin page before giving the tester the jar. Password approval lasts for the current game session and is checked online. Revoking an account blocks its next login; someone who is already playing keeps access until they close the game.

This is a beta access gate, not DRM: a determined user can modify a client mod to remove local checks. The hosted verifier keeps the password list out of the jar and lets you revoke accounts centrally.

# Set up Habib Client usage stats

The in-game Stats panel reads aggregate counts from a small Cloudflare Worker backed by D1. This runs in your Cloudflare account. Each player must turn on **Share anonymous usage** in the Stats tab before their installation is counted.

## Deploy the stats service

You need a Cloudflare account and Node.js installed on your computer.

1. Open Terminal and go to the `usage-stats` folder inside your Habib Client repository.
2. Sign in to Cloudflare by running `npx wrangler@latest login`.
3. Create the database by running `npx wrangler@latest d1 create habib-client-usage-stats`.
4. Cloudflare prints a database ID. Open `wrangler.jsonc` and replace `REPLACE_WITH_DATABASE_ID` with that ID.
5. Create the database table by running `npx wrangler@latest d1 execute habib-client-usage-stats --remote --file=./schema.sql`.
6. Publish the Worker by running `npx wrangler@latest deploy`.
7. Copy the public `https://...workers.dev` address shown after deployment.

## Connect the mod builds

1. Open the Habib Client repository on GitHub and choose **Settings**.
2. Open **Secrets and variables** → **Actions** → **Variables**.
3. Choose **New repository variable**. Set the name to `HABIB_ANALYTICS_URL` and the value to the Worker address you copied. Do not add `/api/ping` or `/api/stats` to the end.
4. Save the variable. The next published mod version will include that address. Until then, the Stats panel will say the service needs setup.

The Worker accepts only small pings containing a random installation ID, the mod version and the Minecraft version. It hashes the ID before saving it, stores one record per installation per day, and deletes records older than 35 days. It does not store player names, Minecraft account IDs, worlds, server addresses or IP addresses. Cloudflare still processes standard network metadata when handling requests.

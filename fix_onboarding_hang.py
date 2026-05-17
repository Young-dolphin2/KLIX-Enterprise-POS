import re

filepath = r"C:\Users\HP\AndroidStudioProjects\BarAndGrillPOS\BarAndGrillOwnerPanel\src\main\kotlin\com\example\barandgrillownerpanel\ui\onboarding\OnboardingScreen.kt"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# Remove the entire "Push to Supabase" block that hangs on auth.updateUser
old_block = """                                    // 4. Push to Supabase (Non-blocking)
                                    scope.launch {
                                        try {
                                            SupabaseManager.client.auth.updateUser {
                                                data {
                                                    put("business_name", kotlinx.serialization.json.JsonPrimitive(businessName))
                                                    put("country", kotlinx.serialization.json.JsonPrimitive(country))
                                                    put("currency_code", kotlinx.serialization.json.JsonPrimitive(currencyCode))
                                                    put("currency_symbol", kotlinx.serialization.json.JsonPrimitive(currencySymbol))
                                                }
                                            }
                                            SupabaseManager.client.postgrest["app_settings"].upsert(
                                                mapOf(
                                                    "business_name" to businessName,
                                                    "country" to country,
                                                    "currency_symbol" to currencySymbol
                                                )
                                            )
                                        } catch (e: Exception) {
                                            println("Supabase sync failed: ${e.message}")
                                        }
                                    }"""

new_block = """                                    // 4. Supabase push skipped (offline/test mode)
                                    // Will be synced by SyncEngine when online"""

if old_block in content:
    content = content.replace(old_block, new_block)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    print("SUCCESS: Removed hanging Supabase auth call from onboarding")
else:
    print("FAILED: Could not find the Supabase block")
    # Debug
    idx = content.find("Push to Supabase")
    if idx >= 0:
        print(f"Found at index {idx}")
        print(content[idx:idx+500])

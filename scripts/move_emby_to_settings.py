from pathlib import Path

main = Path('app/src/main/java/com/nuvio/tv/MainActivity.kt')
text = main.read_text()
text = text.replace('''                            add(Screen.Emby.route)\n''', '', 1)
text = text.replace('''                            add(\n                                DrawerItem(\n                                    route = Screen.Emby.route,\n                                    label = "Emby",\n                                    icon = Icons.Default.LiveTv\n                                )\n                            )\n''', '', 1)
main.write_text(text)

settings = Path('app/src/main/java/com/nuvio/tv/ui/screens/settings/SettingsScreen.kt')
text = settings.read_text()
text = text.replace('''    onNavigateToIptvPairing: () -> Unit = {},\n    profileViewModel:''', '''    onNavigateToIptvPairing: () -> Unit = {},\n    onNavigateToEmbyConnect: () -> Unit = {},\n    profileViewModel:''', 1)
text = text.replace('''                                onNavigateToIptvPairing = onNavigateToIptvPairing\n''', '''                                onNavigateToIptvPairing = onNavigateToIptvPairing,\n                                onNavigateToEmbyConnect = onNavigateToEmbyConnect\n''', 1)
text = text.replace('''                        onNavigateToIptvPairing = onNavigateToIptvPairing\n''', '''                        onNavigateToIptvPairing = onNavigateToIptvPairing,\n                        onNavigateToEmbyConnect = onNavigateToEmbyConnect\n''', 1)
text = text.replace('''    onNavigateToIptvPairing: () -> Unit\n) {''', '''    onNavigateToIptvPairing: () -> Unit,\n    onNavigateToEmbyConnect: () -> Unit\n) {''', 1)
text = text.replace('''            onNavigateToIptvPairing = onNavigateToIptvPairing,\n            autoFocusEnabled = allowDetailAutofocus''', '''            onNavigateToIptvPairing = onNavigateToIptvPairing,\n            onNavigateToEmbyConnect = onNavigateToEmbyConnect,\n            autoFocusEnabled = allowDetailAutofocus''', 1)
text = text.replace('''    onNavigateToIptvPairing: () -> Unit,\n    autoFocusEnabled: Boolean''', '''    onNavigateToIptvPairing: () -> Unit,\n    onNavigateToEmbyConnect: () -> Unit,\n    autoFocusEnabled: Boolean''', 1)
needle = '''                            item(key = "integration_hub_iptv") {\n                                SettingsActionRow(\n                                    title = "IPTV (Xtream Codes)",\n                                    subtitle = "Add a live TV / VOD provider by URL",\n                                    onClick = { onSelectSection(IntegrationSettingsSection.Iptv) }\n                                )\n                            }\n'''
insert = '''                            item(key = "integration_hub_emby") {\n                                SettingsActionRow(\n                                    title = "Emby Connect",\n                                    subtitle = "Connect or manage your Emby server",\n                                    onClick = onNavigateToEmbyConnect\n                                )\n                            }\n''' + needle
if 'integration_hub_emby' not in text:
    text = text.replace(needle, insert, 1)
settings.write_text(text)

nav = Path('app/src/main/java/com/nuvio/tv/ui/navigation/NuvioNavHost.kt')
text = nav.read_text()
text = text.replace('''                onNavigateToIptvPairing = { navController.navigate(Screen.IptvPairing.route) }\n''', '''                onNavigateToIptvPairing = { navController.navigate(Screen.IptvPairing.route) },\n                onNavigateToEmbyConnect = { navController.navigate(Screen.Emby.route) }\n''', 1)
nav.write_text(text)

/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.him188.ani.android.activity

//class SettingsActivity : AniComponentActivity() {
//    companion object {
//        fun getIntent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        requestWindowFeature(Window.FEATURE_NO_TITLE)
//
//        setContent {
//            MaterialTheme(currentColorScheme) {
//                ImmerseStatusBar(AppTheme.colorScheme.primary)
//
//                CommonAppScaffold(
//                    topBar = {
//                        CommonTopAppBar(
//                            navigationIcon = {
//                                IconButton(onClick = { finish() }) {
//                                    Icon(
//                                        Icons.Default.ArrowBack, LocalI18n.current.getString("menu.back")
//                                    )
//                                }
//                            },
//                            title = {
//                                Text(text = LocalI18n.current.getString("window.settings.title"))
//                            },
//                        )
//                    },
//                ) {
//                    Box(modifier = Modifier.padding(vertical = 16.dp)) {
//                        SettingsPage(snackbarHostState)
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun SettingsPage(snackbar: SnackbarHostState) {
//    val manager = LocalAppSettingsManager.current
//    val settings by manager.value.collectAsStateWithLifecycle()
//    val scope = rememberCoroutineScope()
//    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
//        ProxySettingsGroup(
//            settings,
//            manager,
//            disabledButtonText = { Text(LocalI18n.current.getString("preferences.proxy.mode.system")) },
//            disabledContent = {
//                Text(LocalI18n.current.getString("preferences.proxy.mode.system.content"))
//            }
//        )
//        val i18n by rememberUpdatedState(LocalI18n.current)
//        SyncSettingsGroup(settings, manager, onSaved = {
//            scope.launch {
//                snackbar.showSnackbar(
//                    i18n.getString("preferences.sync.changes.apply.on.restart"),
//                    withDismissAction = true
//                )
//            }
//        })
//    }
//}

package com.blockpuzzle.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.blockpuzzle.game.ui.GameScreen
import com.blockpuzzle.game.ui.theme.BlockPuzzleTheme

/**
 * The only screen in the app.
 *
 * The game is fully offline: no network permission, no ad SDK, no analytics. State is kept
 * in [GameViewModel] and mirrored to SharedPreferences on every move, so a run survives
 * rotation, backgrounding and process death alike.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BlockPuzzleTheme {
                GameScreen(viewModel)
            }
        }
    }

    override fun onStop() {
        // Belt and braces: the ViewModel already persists after every drop.
        viewModel.persist()
        super.onStop()
    }
}

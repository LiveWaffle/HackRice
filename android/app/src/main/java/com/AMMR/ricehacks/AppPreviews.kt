package com.AMMR.ricehacks

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.AMMR.ricehacks.data.FakeQrAccessRepository
import com.AMMR.ricehacks.ui.theme.RiceHacksTheme

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LoggedInHomeScreenPreview() {
    RiceHacksTheme(dynamicColor = false) {
        LoggedInHomeScreen(qrAccessRepository = FakeQrAccessRepository())
    }
}

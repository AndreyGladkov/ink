package io.github.andreygladkov.inklauncher.home

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LayoutJsonTest {
    @Test fun everyKindOfTileSurvivesARoundTrip() {
        val layout = HomeLayout(GridSpec(5, 8, 3, 2), pageCount = 2)
            .place(WidgetTile(1, 42, AppKey("com.clock", "com.clock.Widget")), Spot(Sheet.Top, 0, 0, 5, 3))
            .place(AppTile(2, AppKey("com.mail", "com.mail.Main"), label = "Почта", icon = CustomIcon.Text("П"), labelHidden = true), Spot(Sheet.Bottom, 1, 1))
            .place(
                FolderTile(3, "Tools", listOf(
                    AppTile(4, AppKey("a", "a.A"), icon = CustomIcon.FromPack("pack", "a_icon")),
                    AppTile(5, AppKey("b", "b.B"), icon = CustomIcon.Picture("5.png")),
                )),
                Spot(Sheet.Page(1), 2, 0),
            )
            .place(ScreenTimeTile(6), Spot(Sheet.Page(0), 0, 0, 2, 1))
        assertEquals(layout, LayoutJson.read(LayoutJson.write(layout)))
    }

    @Test fun aShortClassNameIsExpandedWithThePackage() {
        assertEquals(AppKey("com.mail", "com.mail.Main"), AppKey.unflatten("com.mail/.Main"))
    }
}

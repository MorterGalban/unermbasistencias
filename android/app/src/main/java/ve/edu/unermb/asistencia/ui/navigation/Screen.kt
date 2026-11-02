package ve.edu.unermb.asistencia.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Login : Screen("login_screen")
    object Register : Screen("register_screen")
    object DocenteDashboard : Screen("docente_dashboard_screen")
    object SuperrootDashboard : Screen("superroot_dashboard_screen")
}


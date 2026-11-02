package ve.edu.unermb.asistencia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ve.edu.unermb.asistencia.ui.auth.LoginScreen
import ve.edu.unermb.asistencia.ui.auth.LoginViewModel
import ve.edu.unermb.asistencia.ui.auth.RegisterScreen
import ve.edu.unermb.asistencia.ui.auth.RegisterViewModel
import ve.edu.unermb.asistencia.ui.docente.DocenteDashboardScreen
import ve.edu.unermb.asistencia.ui.docente.DocenteViewModel
import ve.edu.unermb.asistencia.ui.superroot.SuperrootDashboardScreen
import ve.edu.unermb.asistencia.ui.superroot.SuperrootViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = hiltViewModel()
            LoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = { destinationRoute ->
                    navController.navigate(destinationRoute) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            val viewModel: RegisterViewModel = hiltViewModel()
            RegisterScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.DocenteDashboard.route) {
            val viewModel: DocenteViewModel = hiltViewModel()
            DocenteDashboardScreen(
                viewModel = viewModel,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SuperrootDashboard.route) {
            val viewModel: SuperrootViewModel = hiltViewModel()
            SuperrootDashboardScreen(
                viewModel = viewModel,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}


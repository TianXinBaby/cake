import 'element-plus/dist/index.css'
import './styles/base.css'
import { createApplication } from './application.js'

const { app, router } = createApplication()

router.isReady().then(() => app.mount('#app'))

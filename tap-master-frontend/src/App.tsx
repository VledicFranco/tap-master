import { useState } from 'react'
import './App.css'
import Registration from './Registration'
import Game from './Game'

const App = () => {
  const [ws, setWS] = useState<[string, string, WebSocket] | null>(null)
  return ws ? <Game name={ws[0]} host={ws[1]} ws={ws[2]} onRestart={() => setWS(null)} /> : <Registration onRegister={(name, host, ws) => setWS([name, host, ws])} />
}

export default App

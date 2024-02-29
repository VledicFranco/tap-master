import { useState } from "react"
import styled from "styled-components"

const Container = styled.div`
    width: calc(100vw - 40px);
    padding: 20px;
    display: flex;
    justify-content: center;
    align-items: center;
`

const Form = styled.div`
    width: 300px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    gap: 10px;
    background-color: #eee;
    padding: 20px;
    border-radius: 10px;
    box-shadow: 0 0 10px 0 #000;
`

const NameInput = styled.input`
    width: calc(100% - 20px);
    height: 30px;
    border: 0px;
    border-bottom: 2px solid #000;
    padding: 2px 10px;
    border-radius: 5px;
`

const RegisterButton = styled.button`
    width: 100%;
    height: 30px;
    background-color: #fff;
    border: 0px;
    border-radius: 5px;
    box-shadow: 0 2px 2px 0 #aaa;
`

const Message = styled.div`
    padding: 20px;
    width: 200px;
    color: red;
`

const Registration = ({ onRegister }: { onRegister: (name: string, host: string, ws: WebSocket) => void }) => {
    const [name, setName] = useState('')
    const host = window.location.host.split(":")[0]
    const [ip, setIp] = useState(host+":8081")
    const [message, setMessage] = useState('')
    return <Container>
        <Form>
            <NameInput placeholder="Backend" value={ip} onChange={event => setIp(event.target.value)} />
            <NameInput placeholder="Name" value={name} onChange={event => setName(event.target.value)} />
            <RegisterButton onClick={event => {
                const socket = new WebSocket(`ws://${ip}/api/ws/${name}`)
                socket.onclose = (event) => { setName(""); setMessage(`Name ${name} is already taken`)}
                socket.onopen = () => onRegister(name, ip, socket)
            }}>Join Game</RegisterButton>
            <Message>{message}</Message>
        </Form>
    </Container>
}
export default Registration
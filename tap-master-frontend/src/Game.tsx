import { useEffect, useState } from "react"
import styled from "styled-components"

const Container = styled.div`
    width: 100vw;
    height: 100vh;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
`

const PlayerList = styled.div`
    width: 300px;
    height: 300px;
    overflow-y: scroll;
    overflow-x: hidden;
    background-color: #eee;
    padding: 10px;
    border-radius: 10px;
    box-shadow: 0 0 10px 0 #000;
`

const PlayerListEntry = styled.div`
    margin: 5px 0;
    border-bottom: 1px solid;
    padding: 5px;
`

const TapButtonContainer = styled.div`
    width: calc(100vw - 40px);
    max-width: 500px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    gap: 20px;
`

const TapButton = styled.button<{ color: string }>`
    font-size: 50px;
    border: 0px;
    border-bottom: 1px solid;
    border-radius: 5px;
    text-align: center;
    color: #fff;
    background-color: ${props => props.color};

    &:hover {
        cursor: pointer;
    }
    &:active {
        transform: translateY(2px);
    }
`

const TapString = styled(TapButton)`

`

const TapColor = styled(TapButton)`
`

type Player = { playerId: string, name: string }

type StartedUpdate =
    { event: "started-update", blueTeam: Player[], redTeam: Player[], colors: string, numbers: number, strings: string }

type TapMasterEvent 
    = StartedUpdate
    | { event: "not-started-update", players: Player[] }
    | { event: "finished-update", winner: string }
    | { event: "unknown-event" }

const TapButtons = ({ event, onTapString, onTapNumber, onTapColor }: { 
    event: StartedUpdate, 
    onTapString: () => void, 
    onTapNumber: () => void, 
    onTapColor: () => void 
}) => {
    const string = event.strings === "" ? "Tap String" : event.strings
    const number = event.numbers === 0 ? "Tap Number" : event.numbers
    const stringColor = event.strings === "" ? "#eee;" : event.strings.includes("r") ? "rgb(255, 0, 0);" : "rgb(0, 0, 255);"
    const numberColor = event.numbers === 0 ? "#eee;" : event.numbers < 0 ? "rgb(255, 0, 0);" : "rgb(0, 0, 255);"
    return <TapButtonContainer>
        <TapString color={stringColor} onClick={onTapString}>{string}</TapString>
        <TapButton color={numberColor} onClick={onTapNumber}>{number}</TapButton>
        <TapColor color={event.colors} onClick={onTapColor}>Tap Color</TapColor>
    </TapButtonContainer>
}

const parseEvent = (rawEvent: any): TapMasterEvent => {
    if (typeof rawEvent !== "string") 
        return { event: "unknown-event" }
    const event = JSON.parse(rawEvent)
    return parseJsonEvent(event)
}

const parseJsonEvent = (event: any): TapMasterEvent => {
    if (event["Started"]) {
        const colors = event["Started"].colors
        return { 
            event: "started-update",
            blueTeam: event["Started"].blueTeam,
            redTeam: event["Started"].redTeam,
            colors: `rgb(${colors.r}, ${colors.g}, ${colors.b});`,
            numbers: event["Started"].numbers,
            strings: event["Started"].strings
        }

    } else if (event["NotStarted"]) {
        return { 
            event: "not-started-update", 
            players: event["NotStarted"].registeredPlayers
        }

    } else if (event["Finished"]) {
        return { 
            event: "finished-update",
            winner: event["Finished"].winner["Red"] ? "Red" : "Blue"
        }

    } else {
        return { event: "unknown-event" }
    }
}

const Players = ({ players }: { players: Player[] }) => 
    <PlayerList>
        { players.map((player, index) => <PlayerListEntry key={index}>{player.name}</PlayerListEntry>) }
    </PlayerList>

const Title = styled.h1<{ color: string }>`
    color: ${props => props.color};
`

const Game = ({ name, ws, host, onRestart }: { name: string, ws: WebSocket, host: string, onRestart: () => void }) => {
    const [log, setLog] = useState<TapMasterEvent | null>(null)
    const [myTeam, setTeam] = useState<string>("unknown")

    useEffect(() => {
        fetch(`http://${host}/api/state`)
            .then(response => response.json())
            .then(json => setLog(parseJsonEvent(json)))
        ws.onmessage = rawEvent => {
            const event = parseEvent(rawEvent.data)
            const notInGame = event.event === "not-started-update" && !event.players.find(player => player.name === name)
            if (event.event === "started-update" && myTeam === "unknown")
                setTeam(event.blueTeam.find(player => player.name === name) ? "Blue" : "Red")
            if (notInGame) {
                ws.close()
                onRestart()
            } else if (event.event === "finished-update" && event.winner === name)
                ws.close()
            setLog(event)
        }
    }, [])
    
    const tapTitle = 
        !log || log && log.event === "not-started-update" ? "Get Ready to TAAAAP!!" :
        log && log.event === "started-update" ? `TAAAAP!!` :
        log && log.event === "finished-update" && log.winner == myTeam ? "You Won!!" : 
        log && log.event === "finished-update" && log.winner != myTeam ? "Other team won :(" : 
        "Unknown Event"

    const teamColor = myTeam === "unknown" ? "black;" : myTeam === "Blue" ? "rgb(0, 0, 255);" : "rgb(255, 0, 0);"
    const winnerColor = log && log.event === "finished-update" && log.winner === "Red" ? "rgb(255, 0, 0);" : "rgb(0, 0, 255);"

    const tapString = () => ws.send("tap-string")
    const tapNumber = () => ws.send("tap-number")
    const tapColor = () => ws.send("tap-color")

    return <Container>
        <Title color={log?.event === "finished-update" ? winnerColor : teamColor}>{tapTitle}</Title>
        { log && log.event === "not-started-update" ? <Players players={log.players}/> : <></> }
        { log && log.event === "started-update" ? <TapButtons event={log} onTapColor={tapColor} onTapNumber={tapNumber} onTapString={tapString}/> : <></> }
    </Container>
}

export default Game
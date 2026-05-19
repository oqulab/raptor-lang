package kz.oqulab.raptor.utls

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

object RaptorJson {
    val mJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
        allowSpecialFloatingPointValues = true
        serializersModule = SerializersModule {
//            polymorphic(LobbyMessage::class) {
//                subclass(LobbyMessage.InitialRooms::class, LobbyMessage.InitialRooms.serializer())
//                subclass(LobbyMessage.RoomCreated::class, LobbyMessage.RoomCreated.serializer())
//                subclass(LobbyMessage.RoomRemoved::class, LobbyMessage.RoomRemoved.serializer())
//                subclass(LobbyMessage.RoomUpdated::class, LobbyMessage.RoomUpdated.serializer())
//                subclass(LobbyMessage.RoomBackTo::class, LobbyMessage.RoomBackTo.serializer())
//                subclass(LobbyMessage.SoloVsAi::class, LobbyMessage.SoloVsAi.serializer())
//            }
        }
    }
}
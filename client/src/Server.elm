module Server exposing (ComboDto, CombosDto, UserDto, loadCombos)

import Http
import Json.Decode as Decode exposing (Decoder)
import Json.Encode as Encode exposing (Value)


type alias UserDto =
    { firstName : String
    , lastName : String
    , birthday : String
    , city : String
    , email : String
    , phone : String
    , occupation : Int
    , fieldOfWork : Int
    , englishLevel : Int
    , itExperience : Bool
    , experienceDescription : Maybe String
    , heardFrom : String
    }


encodeMaybe : (a -> Value) -> Maybe a -> Value
encodeMaybe f a =
    case a of
        Just b ->
            f b

        Nothing ->
            Encode.null


encodeUserDto : UserDto -> Value
encodeUserDto a =
    Encode.object
        [ ( "firstName", Encode.string a.firstName )
        , ( "lastName", Encode.string a.lastName )
        , ( "birthday", Encode.string a.birthday )
        , ( "city", Encode.string a.city )
        , ( "email", Encode.string a.email )
        , ( "phone", Encode.string a.phone )
        , ( "occupation", Encode.int a.occupation )
        , ( "fieldOfWork", Encode.int a.fieldOfWork )
        , ( "englishLevel", Encode.int a.englishLevel )
        , ( "itExperience", Encode.bool a.itExperience )
        , ( "experienceDescription", encodeMaybe Encode.string a.experienceDescription )
        , ( "heardFrom", Encode.string a.heardFrom )
        ]


type alias ComboDto =
    { id : Int
    , value : String
    , label : String
    }


type alias CombosDto =
    { occupation : List ComboDto
    , fieldOfWork : List ComboDto
    , englishLevel : List ComboDto
    }


decodeComboDto : Decoder ComboDto
decodeComboDto =
    Decode.map3
        ComboDto
        (Decode.field "id" Decode.int)
        (Decode.field "value" Decode.string)
        (Decode.field "label" Decode.string)


decodeCombosDto : Decoder CombosDto
decodeCombosDto =
    Decode.map3
        CombosDto
        (Decode.field "occupation" (Decode.list decodeComboDto))
        (Decode.field "fieldOfWork" (Decode.list decodeComboDto))
        (Decode.field "englishLevel" (Decode.list decodeComboDto))


loadCombos : String -> (Result Http.Error CombosDto -> msg) -> Cmd msg
loadCombos baseUrl ctor =
    Http.get
        { url = baseUrl ++ "/combos/en"
        , expect = Http.expectJson ctor decodeCombosDto
        }

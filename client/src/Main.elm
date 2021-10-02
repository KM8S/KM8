module Main exposing (..)

import Browser
import Browser.Navigation as Nav
import Config exposing (..)
import Element exposing (..)
import Element.Background as Background
import Element.Border as Border
import Element.Font as Font
import Html exposing (Html, div)
import Http
import Main.Models exposing (ClusterDetailsDTO)
import Server exposing (..)
import Time exposing (..)
import Url


initClusters : List ClusterDetailsDTO
initClusters =
    [ ClusterDetailsDTO "1" "Local Kafka" "http://localhost:9092" "http://localhost:8000"
    , ClusterDetailsDTO "2" "Dev Kafka" "http://dev.remote.example.com:9092" "http://dev.remote.example.com:8000"
    , ClusterDetailsDTO "3" "Test Kafka" "http://test.remote.example.com:9092" "http://test.remote.example.com:8000"
    ]



---- MODEL ----


type alias Model =
    { baseUrl : String
    , key : Nav.Key
    , url : Url.Url
    , error : Maybe String
    , clusters : List ClusterDetailsDTO
    }


init : Config -> Url.Url -> Nav.Key -> ( Model, Cmd Msg )
init flags url key =
    ( Model flags.baseUrl key url Nothing initClusters, Cmd.none )



---- UPDATE ----


type Msg
    = LinkClicked Browser.UrlRequest
    | UrlChanged Url.Url


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        LinkClicked urlRequest ->
            case urlRequest of
                Browser.Internal url ->
                    ( model, Nav.pushUrl model.key (Url.toString url) )

                Browser.External href ->
                    ( model, Nav.load href )

        UrlChanged url ->
            ( { model | url = url }, Cmd.none )



---- VIEW ----


view : Model -> Browser.Document Msg
view model =
    { title = "Kafka Mate"
    , body = [ Element.layout [] (body model) ]
    }


body : Model -> Element msg
body model =
    column
        [ width fill
        , height fill
        ]
        [ header model, content model, footer model ]


header : Model -> Element msg
header model =
    row [ width fill ]
        [ image [ padding 20, width (px 100), height (px 100), alignLeft ] { src = "logo.svg", description = "logo" }
        , el [ padding 20, alignRight ] (menu model)
        ]


green : Color
green =
    rgb255 123 182 105


white : Color
white =
    rgb255 240 240 240


menuButton : String -> Element msg
menuButton txt =
    el
        [ Border.rounded 5
        , mouseOver [ Font.color green ]
        , pointer
        , padding 10
        ]
        (text txt)


menu : Model -> Element msg
menu model =
    row [ alignRight ]
        [ column [ height fill ]
            [ menuButton "Clusters" ]
        , column [ height fill ]
            [ el [ centerY ] (text "|") ]
        , column [ height fill ]
            [ menuButton "Help" ]
        ]


rowContent : Model -> Element msg
rowContent model =
    row [ explain Debug.todo, width fill, height fill ] [ menu model, content model ]


gridHeader : String -> Element msg
gridHeader txt =
    el [ paddingEach { top = 5, right = 5, bottom = 5, left = 0 }, width fill ]
        (el
            [ width fill
            , paddingEach { top = 2, right = 0, bottom = 2, left = 5 }
            , Background.color green
            , Font.alignLeft
            , Font.color white
            ]
            (text txt)
        )


gridCell : String -> Element msg
gridCell txt =
    el
        [ paddingEach { top = 5, right = 5, bottom = 5, left = 3 }
        , Background.color (rgb 255 255 255)
        , Font.alignLeft
        ]
        (text txt)


content : Model -> Element msg
content model =
    column [ padding 10, width fill, height fill ]
        [ Element.table [ padding 10 ]
            { data = model.clusters
            , columns =
                [ { header = gridHeader "Name"
                  , width = fillPortion 2
                  , view = \c -> gridCell c.name
                  }
                , { header = gridHeader "Hosts"
                  , width = fillPortion 5
                  , view = \c -> gridCell c.kafkaHosts
                  }
                , { header = gridHeader "Schema Registry"
                  , width = fillPortion 5
                  , view = \c -> gridCell c.schemaRegistryUrl
                  }
                ]
            }
        ]


footer : Model -> Element msg
footer model =
    row [ width fill ] [ text "footer" ]



---- PROGRAM ----


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none


main : Program Config Model Msg
main =
    Browser.application
        { view = view
        , init = init
        , update = update
        , subscriptions = subscriptions
        , onUrlChange = UrlChanged
        , onUrlRequest = LinkClicked
        }

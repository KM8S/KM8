module Main exposing (..)

import Browser
import Browser.Navigation as Nav
import Config exposing (..)
import Element exposing (..)
import Element.Background as Background
import Element.Border as Border
import Element.Font as Font
import Element.Input as Input
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
    | ClusterChange String


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

        _ ->
            ( model, Cmd.none )



---- VIEW ----


view : Model -> Browser.Document Msg
view model =
    { title = "Kafka Mate"
    , body = [ Element.layout [ Font.size 16, Font.color black ] (body model) ]
    }


body : Model -> Element Msg
body model =
    column
        [ width fill
        , height fill
        ]
        [ header model, content model, footer model ]


header : Model -> Element Msg
header model =
    row [ width fill, Background.color backGreen, height (px 100), centerY ]
        [ image
            [ width (px 100)
            , height (px 100)
            , alignLeft
            ]
            { src = "logo.svg", description = "logo" }
        , el [ padding 20, alignRight ] (menu model)
        ]


green : Color
green =
    rgb255 123 182 105


lightGreen : Color
lightGreen =
    rgb255 133 192 115


backGreen : Color
backGreen =
    rgb255 240 255 245


white : Color
white =
    rgb255 240 240 240


black : Color
black =
    rgb255 70 70 70


menuButton : String -> Element msg
menuButton txt =
    el
        [ mouseOver [ Font.color green ]
        , pointer
        , padding 10
        ]
        (text txt)


menu : Model -> Element msg
menu _ =
    row [ alignRight ]
        [ column [ height fill ]
            [ menuButton "Clusters" ]
        , column [ height fill ]
            [ el [ centerY ] (text "|") ]
        , column [ height fill ]
            [ menuButton "Help" ]
        ]


clusterDataList : Model -> Element Msg
clusterDataList model =
    let
        altColor i =
            if i == 0 || modBy 2 i == 0 then
                rgb255 255 255 255

            else
                backGreen

        gridHeader txt =
            el
                [ paddingEach { top = 5, right = 5, bottom = 5, left = 0 }
                , width fill
                , height fill
                ]
                (el
                    [ width fill
                    , height fill
                    , paddingEach { top = 10, right = 0, bottom = 10, left = 5 }
                    , Background.color green
                    , Border.rounded 2
                    , Font.color white
                    , centerY
                    ]
                    (text txt)
                )

        lstButton idx m txt =
            el [ padding 5, Background.color <| altColor idx ]
                (Input.button
                    [ Background.color green
                    , Font.color white
                    , padding 8
                    , Element.mouseOver [ Background.color lightGreen ]
                    , Element.focused [ Background.color lightGreen ]
                    , Border.rounded 2
                    ]
                    { onPress = m
                    , label = text txt
                    }
                )

        gridCell idx txt =
            el
                [ Background.color <| altColor idx
                , height fill
                , paddingEach { top = 5, right = 5, bottom = 5, left = 0 }
                ]
                (el
                    [ paddingEach { top = 0, right = 0, bottom = 0, left = 5 }
                    , centerY
                    ]
                    (text txt)
                )
    in
    Element.indexedTable [ padding 10, width fill, Font.alignLeft ]
        { data = model.clusters
        , columns =
            [ { header = gridHeader "Name"
              , width = shrink
              , view = \i c -> gridCell i c.name
              }
            , { header = gridHeader "Hosts"
              , width = shrink
              , view = \i c -> gridCell i c.kafkaHosts
              }
            , { header = gridHeader "Schema Registry"
              , width = shrink
              , view = \i c -> gridCell i c.schemaRegistryUrl
              }
            , { header = gridHeader ""
              , width = shrink
              , view =
                    \i c ->
                        row [ width shrink ]
                            [ lstButton i (Just <| ClusterChange c.id) "Change"
                            , lstButton i (Just <| ClusterChange c.id) "Delete"
                            ]
              }
            ]
        }


content : Model -> Element Msg
content model =
    column [ padding 10, height fill, centerX ]
        [ clusterDataList model ]


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

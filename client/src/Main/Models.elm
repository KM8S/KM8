module Main.Models exposing (..)

import List exposing (..)


type alias ClusterDetailsDTO =
    { id : String
    , name : String
    , kafkaHosts : String
    , schemaRegistryUrl : String
    }


type alias ClusterResponseDTO =
    { brokers : List String
    }

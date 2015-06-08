package io.surfkit.client

import io.surfkit.model.Auth
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

trait Friends {

}

object Friends{
  import io.surfkit.client.Base._

  type FriendSelectEvent = (Auth.ProfileInfo) => Unit
  type FriendAddEvent = (Set[Auth.ProfileInfo]) => Unit

  case class FriendEvents(onFilterChange:ReactEvent, onFriendSelect:FriendSelectEvent, onFriendAddEvent:FriendAddEvent)
  case class FriendsState(friends:Seq[Auth.ProfileInfo], filter:String, events:FriendEvents)

  val FriendCard = ReactComponentB[(Auth.ProfileInfo, FriendSelectEvent)]("FriendCard")
    .render(props => {
      val (friend,onSelect) = props
      //<.div(^.className:="friend-card",^.onClick --> onSelect(friend),
      <.div(^.className:="friend-card",^.onClick ==> ((r:ReactEventI) => {
        r.target.className =
          if(r.target.className.contains(" active"))
            r.target.className.replace(" active","")
          else
            r.target.className + " active"
        onSelect(friend)
      }) ,
          <.img(^.src:= friend.avatarUrl, ^.className:="avatar"),
          <.span(friend.fullName)
        )
      })
      .build

  val FriendList = ReactComponentB[(FriendsState)]("FriendList")
    .render(props => {
      val (friends) = props
      val name = friends.filter.toLowerCase
      <.div(^.className:="friend-list",
        <.header(^.className:="tools",
          SearchBar( (friends.filter,friends.events.onFilterChange, null,"fa fa-search") )
        ),
        <.div(friends.friends.filter(_.fullName.toLowerCase.contains(name)).map(f => FriendCard( (f,friends.events.onFriendSelect) ) ))
      )
    })
    .build


  val FriendSelector = ReactComponentB[(FriendsState, Set[Auth.ProfileInfo])]("FriendFinder")
    .render(props => {
      val (friends,m) = props
      var members = m
      val name = friends.filter.toLowerCase
      val friendList =
        if(name.length > 0)
          friends.friends.filter(_.fullName.toLowerCase.contains(name))
        else
          Nil
      <.div(^.className:="friend-list",
        <.header(^.className:="tools",
          SearchBar( (friends.filter,friends.events.onFilterChange, (i:String) =>{
            friends.events.onFriendAddEvent(members)
          },"fa fa-plus") )
        ),
        <.div(friendList.map(f => FriendCard( (f,(friend:Auth.ProfileInfo)=>{
          members =
            if( members.contains(friend) )
              members - friend
            else
              members + friend
        }) ) ))
      )
    })
    .build

}

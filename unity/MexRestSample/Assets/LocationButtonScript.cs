﻿using UnityEngine;

using UnityEngine.UI;

public class LocationButtonScript : MonoBehaviour
{
  public Button locationUpdateButton;

  void Start()
  {
    Button btn = locationUpdateButton.GetComponent<Button>();
    btn.onClick.AddListener(TaskOnClick);
  }

  async void TaskOnClick()
  {
    Debug.Log("location button clicked!");
    var loc = await LocationService.RetrieveLocation();
    Debug.Log("Location: Lat: " + loc.latitude + ", Long: " + loc.longitude);
  }

}


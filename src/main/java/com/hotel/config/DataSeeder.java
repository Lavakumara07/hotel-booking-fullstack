package com.hotel.config;

import com.hotel.model.Room;
import com.hotel.repository.RoomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

	private final RoomRepository roomRepository;

	public DataSeeder(RoomRepository roomRepository) {
		this.roomRepository = roomRepository;
	}

	@Override
	public void run(String... args) {
		if (roomRepository.count() > 0) {
			return;
		}

		roomRepository.saveAll(List.of(
				createRoom(
						"101",
						"Garden Suite",
						"14500.00",
						"A serene suite with warm natural textures and a restful garden view.",
						"https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=1200&q=80"),
				createRoom(
						"202",
						"Heritage King",
						"17250.00",
						"A spacious king room pairing timeless details with considered comforts.",
						"https://images.unsplash.com/photo-1618221195710-dd6b41faaea6?auto=format&fit=crop&w=1200&q=80"),
				createRoom(
						"303",
						"Terrace Premier",
						"21900.00",
						"An airy hideaway with a private terrace and room to truly unwind.",
						"https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80"),
				createRoom(
						"404",
						"Grand Residence",
						"28600.00",
						"A generous residence designed for slow mornings and special stays.",
						"https://images.unsplash.com/photo-1613977257363-707ba9348227?auto=format&fit=crop&w=1200&q=80")
		));
	}

	private Room createRoom(String number, String type, String price, String description, String imageUrl) {
		Room room = new Room();
		room.setRoomNumber(number);
		room.setRoomType(type);
		room.setPricePerNight(new BigDecimal(price));
		room.setDescription(description);
		room.setImageUrl(imageUrl);
		room.setAvailable(true);
		return room;
	}
}
